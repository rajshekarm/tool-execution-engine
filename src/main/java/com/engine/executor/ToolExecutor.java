package com.engine.executor;

import com.engine.model.*;
import java.util.concurrent.CompletionStage;
public interface ToolExecutor {
    CompletionStage<ExecutionResult> invoke(ExecutionContext context);
}
