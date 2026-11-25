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

    public DefaultExecutionScheduler(
            int maxConcurrency,
            int queueCapacity) {

        if (maxConcurrency <= 0 || queueCapacity <= 0) {
            throw new IllegalArgumentException(
                    "Limits must be positive"
            );
        }

        workers = new ThreadPoolExecutor(
                maxConcurrency,
                maxConcurrency,
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(queueCapacity),
                Thread.ofVirtual()
                        .name("scheduler-", 0)
                        .factory(),
                new ThreadPoolExecutor.AbortPolicy()
        );
    }

    @Override
    public CompletionStage<ExecutionResult> schedule(
            Supplier<CompletionStage<ExecutionResult>> task) {

        Objects.requireNonNull(task, "task");

        var result = new CompletableFuture<ExecutionResult>();

        try {
            workers.execute(() -> {
                try {
                    result.complete(
                            task.get()
                                    .toCompletableFuture()
                                    .join()
                    );
                } catch (Throwable error) {
                    result.completeExceptionally(error);

                    if (error instanceof Error fatal) {
                        throw fatal;
                    }
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