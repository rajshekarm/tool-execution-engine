# Tool Execution Engine
Minimal Java 21 / Maven scaffold for five in-process services. No external dependencies.

## Build
`mvn clean package` (requires Maven and JDK 21+).

## Implementation order
1. Registry: ConcurrentHashMap, duplicate registration policy.
2. Executor: virtual-thread ExecutorService for I/O-bound tools; define shutdown ownership.
3. Scheduler: bounded FIFO queue, concurrency limit, explicit rejection; release capacity on completion or failure.
4. Result manager: concurrent state and CompletableFuture; ensure terminal completion exactly once and bound retention.
5. Orchestrator: resolve tool, create handle, queue invocation, update state, complete result on success/failure/rejection.
6. Engine: already wires default components; add lifecycle/shutdown and integration tests.

All five default implementations intentionally throw UnsupportedOperationException until implemented. The scheduler accepts a deferred asynchronous task so it does not need to know tool invocation details. The orchestrator owns lifecycle coordination. Arguments are shallow immutable copies; nested values are not frozen. Cancellation, retries, persistence and networking are intentionally deferred. Test directories are placeholders; no test framework configured yet.
