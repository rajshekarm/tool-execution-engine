package com.engine.model;

import java.util.UUID;
import java.util.concurrent.CompletionStage;
public record ExecutionHandle(UUID id, CompletionStage<ExecutionResult> result) {}
