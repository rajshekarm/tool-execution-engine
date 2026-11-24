package com.engine.scheduler;

import com.engine.model.*;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;
public final class DefaultExecutionScheduler implements ExecutionScheduler {
    public CompletionStage<ExecutionResult> schedule(Supplier<CompletionStage<ExecutionResult>> task) { throw new UnsupportedOperationException("TODO: bounded scheduling"); }
}
