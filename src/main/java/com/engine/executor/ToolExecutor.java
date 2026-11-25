package com.engine.executor;

import com.engine.model.*;
import java.util.concurrent.CompletionStage;
public interface ToolExecutor {
    /**
     * Executes the resolved tool asynchronously using the provided
     * execution context and returns its eventual execution result.
     *
     * @param context the execution request and resolved tool
     * @return a CompletionStage representing the outcome of the execution
     */
    CompletionStage<ExecutionResult> invoke(ExecutionContext context);
}
