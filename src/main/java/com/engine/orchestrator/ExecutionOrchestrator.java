package com.engine.orchestrator;

import com.engine.model.*;

public interface ExecutionOrchestrator {
    ExecutionHandle submit(ExecutionRequest request);
}
