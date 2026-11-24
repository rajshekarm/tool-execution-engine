package com.engine;

import com.engine.model.*;
import com.engine.registry.*;
import com.engine.scheduler.*;
import com.engine.executor.*;
import com.engine.result.*;
import com.engine.orchestrator.*;

public final class ToolExecutionEngine {
    private final ToolRegistry registry;
    private final ExecutionOrchestrator orchestrator;

    public ToolExecutionEngine() {
        registry = new DefaultToolRegistry();
        orchestrator = new DefaultExecutionOrchestrator(registry,
            new DefaultExecutionScheduler(), new DefaultToolExecutor(), new DefaultResultManager());
    }

    public void register(String id, Tool tool) { registry.register(id, tool); }
    public ExecutionHandle submit(ExecutionRequest request) { return orchestrator.submit(request); }
}
