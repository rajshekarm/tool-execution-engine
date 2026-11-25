package com.engine.scheduler;

import com.engine.model.*;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;
public interface ExecutionScheduler {

    /**
     * Schedules an asynchronous tool invocation within
     * the configured execution capacity.
     *
     * @param task the asynchronous execution operation
     * @return a stage representing the execution outcome
     */
    CompletionStage<ExecutionResult> schedule(
            Supplier<CompletionStage<ExecutionResult>> task
    );
}


