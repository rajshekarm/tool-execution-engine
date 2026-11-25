# Execution Scheduler — Design and Working

Tool Execution Engine | Phase 3 | Java 21

## 1. Purpose and boundaries

The scheduler balances incoming demand against available execution capacity. Virtual threads make blocking I/O inexpensive in terms of threads, but do not increase database connections, API quotas, CPU capacity or memory. The scheduler limits outstanding work and rejects excess demand rather than allowing an unlimited backlog.

The registry resolves tools; the scheduler decides when work is admitted; the executor invokes tools; the orchestrator will coordinate these components and the result manager. The scheduler does not resolve tools, interpret arguments, retry operations or persist results.

```text
Agent → Orchestrator → Registry (resolve tool)
                    → Scheduler (admit / queue / reject)
                        → Tool Executor (invoke asynchronously)
                        ← CompletionStage<ExecutionResult>
                    ← Scheduler result stage
                    → Result Manager
```

This is the intended integrated architecture, not a claim that orchestration is already implemented.

## 2. Contract: deferred execution

```java
public interface ExecutionScheduler {
    CompletionStage<ExecutionResult> schedule(
        Supplier<CompletionStage<ExecutionResult>> task);
}
```

A supplier is a function that produces a value when `get()` is called. Here that value is a stage representing an asynchronous tool invocation.

```java
scheduler.schedule(() -> executor.invoke(context));
```

The lambda is created on the submitting thread, but the tool invocation starts only when a scheduler worker calls `task.get()`. Calling `executor.invoke(context)` before scheduling would defeat admission control. `CompletionStage` allows the caller to attach callbacks without waiting for the result.

## 3. Current implementation

The following is the scheduler implementation from the Phase 3 project archive, with formatting expanded for readability.

```java
package com.engine.scheduler;

import com.engine.model.ExecutionResult;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.function.Supplier;

public final class DefaultExecutionScheduler
        implements ExecutionScheduler, AutoCloseable {
    private final ThreadPoolExecutor workers;

    public DefaultExecutionScheduler() {
        this(100, 1000);
    }

    public DefaultExecutionScheduler(int maxConcurrency, int queueCapacity) {
        if (maxConcurrency <= 0 || queueCapacity <= 0) {
            throw new IllegalArgumentException("Limits must be positive");
        }
        workers = new ThreadPoolExecutor(
            maxConcurrency, maxConcurrency,
            0L, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(queueCapacity),
            Thread.ofVirtual().name("scheduler-", 0).factory(),
            new ThreadPoolExecutor.AbortPolicy());
    }

    @Override
    public CompletionStage<ExecutionResult> schedule(
            Supplier<CompletionStage<ExecutionResult>> task) {
        Objects.requireNonNull(task, "task");
        var result = new CompletableFuture<ExecutionResult>();
        try {
            workers.execute(() -> {
                try {
                    result.complete(task.get().toCompletableFuture().join());
                } catch (Throwable error) {
                    result.completeExceptionally(error);
                    if (error instanceof Error fatal) throw fatal;
                }
            });
        } catch (RejectedExecutionException error) {
            result.completeExceptionally(error);
        }
        return result.minimalCompletionStage();
    }

    @Override
    public void close() {
        workers.close();
    }
}
```

## 4. Constructor: define resource boundaries

| Argument | Meaning |
|---|---|
| `maxConcurrency` (first) | Core worker count; workers are created on demand. |
| `maxConcurrency` (second) | Maximum worker count; equal to core count to prevent expansion. |
| `0L, MILLISECONDS` | Keep-alive duration; core workers do not time out by default. |
| `ArrayBlockingQueue` | Fixed-size, thread-safe FIFO buffer of pending Runnables. |
| Virtual thread factory | Creates named virtual workers such as `scheduler-0`. |
| `AbortPolicy` | Throws `RejectedExecutionException` when admission fails. |

The default capacity is 100 worker slots plus 1,000 waiting slots. These are configurable starting values, not benchmark-derived production limits. A worker may be waiting for I/O rather than actively using CPU. The queue bounds task count, not bytes retained by task arguments.

## 5. Admission control: what Java actually does

The real call is `workers.execute(runnable)`. There is no custom admission `if/else` in our scheduler. OpenJDK's `ThreadPoolExecutor.execute` uses the following actual internal operations (described here rather than reproduced as source):

1. Read the packed lifecycle/worker-count state via `ctl.get()`. If the worker count is below the core size, attempt `addWorker(command, true)`.
2. Otherwise, if running, attempt `workQueue.offer(command)`. This is a non-waiting insertion with respect to queue capacity; internal locking can still briefly contend.
3. After successful insertion, recheck pool state. If shutdown raced with admission, attempt to remove and reject the task. If no workers remain, attempt to start one to drain the queue.
4. If insertion failed, attempt `addWorker(command, false)` up to the maximum worker count. In our fixed-size configuration this normally fails at saturation.
5. Invoke `reject(command)` if no worker can be added. `AbortPolicy` throws the rejection exception.

The internal `ctl` is an `AtomicInteger` packing run state and worker count. CAS coordinates worker-count updates; a `ReentrantLock` protects worker-set management. `ArrayBlockingQueue` uses its own lock and conditions to coordinate producers and consumers. These mechanisms avoid the race that would occur if application code independently checked queue size and then inserted a task.

Read the actual JDK implementation: [OpenJDK 21 ThreadPoolExecutor.java](https://github.com/openjdk/jdk21u/blob/master/src/java.base/share/classes/java/util/concurrent/ThreadPoolExecutor.java) (search for `execute`, `addWorker`, `getTask`, `runWorker`, and `AbortPolicy`). This is a reference link; the upstream source was not fetched for this document.

## 6. Worker lifecycle and the two futures

```java
var result = new CompletableFuture<ExecutionResult>();
workers.execute(() -> {
    try {
        CompletionStage<ExecutionResult> execution = task.get();
        ExecutionResult output = execution.toCompletableFuture().join();
        result.complete(output);
    } catch (Throwable error) {
        result.completeExceptionally(error);
        if (error instanceof Error fatal) throw fatal;
    }
});
```

This is an expanded excerpt of the real implementation, not a replacement for its outer rejection handler.

- `result` is the scheduler-owned promise returned to the caller through a restricted stage.
- `execution` is the stage produced by the tool executor when the worker invokes the supplier.
- `join()` waits for the tool stage to finish. It blocks the scheduler worker, not the original submitting thread. Virtual threads can normally unmount from carrier threads during this wait.
- `complete(output)` publishes the outcome. Dependent non-async callbacks may run inline on this worker, so slow callbacks can delay slot reuse.
- Once the Runnable returns, the worker can dequeue another task. A worker is reused rather than destroyed after every invocation.

```text
Submitting thread       Scheduler worker       Executor virtual thread
       |                       |                         |
 schedule(lambda)              |                         |
 create result future          |                         |
 execute(runnable) ----------->|                         |
 return stage                  | task.get() ------------>|
       |                       | join() waits            | tool.invoke()
       |                       |<------------------------| complete stage
       |<----------------------| complete result         |
 callback / await result       | take next queued task   |
```

Without `join()`, the worker could return immediately after starting an asynchronous operation, allowing many more tools to run than the configured limit. With it, each worker retains its slot until the supplied stage completes (assuming that stage accurately represents the underlying operation's lifetime). `join()` is not interruptible in the same way as `get()` and is not a timeout mechanism.

## 7. Saturation and backpressure

For 120 workers, 2,000 queue slots and a burst of 3,000 requests, assuming no completions during admission:

| State | Requests |
|---|---:|
| Assigned to workers | 120 |
| Waiting | 2,000 |
| Rejected | 880 |

When a worker finishes, it takes another queued task. A new request can then occupy the freed queue slot. FIFO applies to removal from the waiting queue, not guaranteed global execution-start or completion order across workers. Concurrent submissions are ordered by actual queue insertion, not by wall-clock arrival timestamps.

This is fail-fast load shedding, a form of overload signaling. It does not automatically slow producers; callers must respond appropriately, for example by limiting submissions or using bounded retries with jitter where safe. The scheduler does not retry on its own.

For a rough steady-state estimate, 100 slots and an average tool duration of 0.5 seconds imply an ideal capacity near 200 operations/second (100 / 0.5), before downstream limits and overhead. If demand persistently exceeds throughput, the queue fills and rejection becomes unavoidable. Increasing the queue increases buffering, not service throughput.

## 8. Error handling and ownership

| Situation | Observable outcome |
|---|---|
| Tool succeeds | Normal stage completion with `SUCCEEDED` result. |
| Tool throws an ordinary exception | Current executor returns a normal stage containing `FAILED` result. |
| Executor is shut down | Current executor can return a normal stage containing `REJECTED` result. |
| Scheduler queue is full or scheduler is closed | Scheduler stage completes exceptionally with rejection. |
| Supplier throws or tool stage fails exceptionally | Scheduler stage completes exceptionally; `join()` may wrap the cause in `CompletionException`. |
| Null supplier | Immediate `NullPointerException` before stage creation. |

The inner catch completes the promise even if a supplier throws an `Error`, then rethrows a directly caught `Error`. An error wrapped by `join()` is not necessarily rethrown as an `Error`. The outer catch handles admission rejection before a worker runs.

`minimalCompletionStage()` protects the scheduler-owned future from external completion. A caller can obtain a separate `CompletableFuture` through `toCompletableFuture()` and wait on it, but cancelling that copy does not cancel the underlying tool.

## 9. Real runnable admission demonstration

Save as `SchedulerDemo.java`. This uses actual Java concurrency primitives, not pseudocode. The latch keeps all admitted work unfinished until the submission loop ends, making saturation reproducible.

```java
import java.util.concurrent.*;

public class SchedulerDemo {
    public static void main(String[] args) {
        var gate = new CountDownLatch(1);
        var workers = new ThreadPoolExecutor(
            2, 2, 0L, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(3),
            Thread.ofVirtual().name("worker-", 0).factory(),
            new ThreadPoolExecutor.AbortPolicy());
        try {
            for (int i = 1; i <= 7; i++) {
                final int id = i;
                try {
                    workers.execute(() -> {
                        try {
                            gate.await();
                            System.out.println("Executing: " + id);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    });
                    System.out.println("Accepted: " + id);
                } catch (RejectedExecutionException e) {
                    System.out.println("Rejected: " + id);
                }
            }
            System.out.println("Workers: " + workers.getPoolSize());
            System.out.println("Queued: " + workers.getQueue().size());
        } finally {
            gate.countDown();
            workers.close();
        }
    }
}
```

Run with Java 21 or later:

```bash
java SchedulerDemo.java
```

Verified admission output (the demo was run with JDK 21; execution messages follow in nondeterministic order):

```text
Accepted: 1
Accepted: 2
Accepted: 3
Accepted: 4
Accepted: 5
Rejected: 6
Rejected: 7
Workers: 2
Queued: 3
```

## 10. Shutdown and limitations

`workers.close()` stops admission and normally waits for accepted tasks to drain. Close the scheduler before the tool executor so queued invocations can still start. Do not call blocking shutdown from one of its own workers. A tool that never completes can prevent shutdown indefinitely. If the closing thread is interrupted, Java's executor close can invoke `shutdownNow`; discarded queued Runnables in this implementation are not explicitly completed, so their result stages may remain pending.

Other limitations: no explicit cancellation propagation, execution deadlines, per-agent or per-tool limits, priorities, persistence, metrics, or adaptive concurrency. A task that schedules another task on the same saturated scheduler and waits for it can deadlock. Two layers of virtual threads are used (pooled scheduler workers and per-task executor threads); this is a simplicity tradeoff, not the usual virtual-thread-per-task design. A future bounded dispatcher with permits and completion callbacks could avoid waiting scheduler workers, but must carefully handle rejection, shutdown and permit release races.

## 11. Verification and next steps

The existing `ExecutionSchedulerTest` uses a scheduler with one worker and one queue slot. It holds the first execution open with a future, verifies the second waits and the third is rejected, releases the first, checks both results, checks supplier failure propagation, and verifies rejection after shutdown.

The Phase 3 archive's Java sources and tests were compiled with JDK 21 in the preparation of this document; running `com.engine.scheduler.ExecutionSchedulerTest` printed `Scheduler tests passed`. Maven was not available. This is a focused functional test, not a stress test or throughput benchmark.

From the extracted project root (Bash):

```bash
mkdir -p out
javac -d out $(find src/main/java src/test/java -name '*.java')
java -cp out com.engine.scheduler.ExecutionSchedulerTest
```

Next, test concurrent producers, maximum observed in-flight work, exceptional completion, shutdown races and never-completing tasks. Integrate the scheduler with the orchestrator and result manager before introducing advanced scheduling policies.

## 12. Further reading

- [Java 21 ThreadPoolExecutor API](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/ThreadPoolExecutor.html)
- [Java 21 ArrayBlockingQueue API](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/ArrayBlockingQueue.html)
- [Java 21 CompletableFuture API](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/CompletableFuture.html)
- [JEP 444: Virtual Threads](https://openjdk.org/jeps/444)

**Core principle:** Incoming demand may be unbounded, but admitted execution capacity must be bounded. The scheduler enforces that boundary while the executor performs the actual work.
