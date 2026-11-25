package com.engine.executor;

import com.engine.model.*;
import java.util.Objects;
import java.util.concurrent.*;

public final class DefaultToolExecutor
        implements ToolExecutor, AutoCloseable {

    private final ExecutorService workers =
            Executors.newVirtualThreadPerTaskExecutor();

    @Override
    public CompletionStage<ExecutionResult> invoke(
            ExecutionContext context) {

        Objects.requireNonNull(context, "context");

        var request = Objects.requireNonNull(
                context.request(), "request"
        );

        var tool = Objects.requireNonNull(
                context.tool(), "tool"
        );

        try {
            return CompletableFuture.supplyAsync(() -> {

                try {
                    Object output = tool.invoke(
                            request.arguments()
                    );

                    return new ExecutionResult(
                            request.id(),
                            ExecutionStatus.SUCCEEDED,
                            output,
                            null
                    );

                } catch (Exception error) {

                    if (error instanceof InterruptedException) {
                        Thread.currentThread().interrupt();
                    }

                    return new ExecutionResult(
                            request.id(),
                            ExecutionStatus.FAILED,
                            null,
                            error
                    );
                }

            }, workers);

        } catch (RejectedExecutionException error) {

            return CompletableFuture.completedFuture(
                    new ExecutionResult(
                            request.id(),
                            ExecutionStatus.REJECTED,
                            null,
                            error
                    )
            );
        }
    }

    @Override
    public void close() {
        workers.close();
    }
}