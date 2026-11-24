package com.engine.model;

import java.util.UUID;
public record ExecutionResult(UUID id, ExecutionStatus status, Object output, Throwable error) {}
