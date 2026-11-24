package com.engine.model;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
public record ExecutionRequest(UUID id, String agentId, String toolId, Map<String, Object> arguments) {
    public ExecutionRequest {
        Objects.requireNonNull(id); Objects.requireNonNull(agentId); Objects.requireNonNull(toolId);
        arguments = Map.copyOf(arguments);
    }
}
