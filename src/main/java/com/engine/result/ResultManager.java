package com.engine.result;

import com.engine.model.*;
import java.util.UUID;
public interface ResultManager {
    ExecutionHandle create(ExecutionRequest request);
    void update(UUID id, ExecutionStatus status);
    void complete(ExecutionResult result);
}
