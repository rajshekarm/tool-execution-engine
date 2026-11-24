package com.engine.orchestrator;

import com.engine.model.*;

import com.engine.registry.ToolRegistry;
import com.engine.scheduler.ExecutionScheduler;
import com.engine.executor.ToolExecutor;
import com.engine.result.ResultManager;
public final class DefaultExecutionOrchestrator implements ExecutionOrchestrator {
    private final ToolRegistry registry;
    private final ExecutionScheduler scheduler;
    private final ToolExecutor executor;
    private final ResultManager results;

    public DefaultExecutionOrchestrator(ToolRegistry registry, ExecutionScheduler scheduler, ToolExecutor executor, ResultManager results) {
        this.registry = registry; this.scheduler = scheduler; this.executor = executor; this.results = results;
    }
    public ExecutionHandle submit(ExecutionRequest request) { throw new UnsupportedOperationException("TODO: coordinate execution lifecycle"); }
}
