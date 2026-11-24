package com.engine.scheduler;

import com.engine.model.*;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;
public interface ExecutionScheduler {
    CompletionStage<ExecutionResult> schedule(Supplier<CompletionStage<ExecutionResult>> task);
}
