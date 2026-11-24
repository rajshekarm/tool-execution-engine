package com.engine.result;

import com.engine.model.*;
import java.util.UUID;
public final class DefaultResultManager implements ResultManager {
    public ExecutionHandle create(ExecutionRequest request) { throw new UnsupportedOperationException("TODO: create handle"); }
    public void update(UUID id, ExecutionStatus status) { throw new UnsupportedOperationException("TODO: update state"); }
    public void complete(ExecutionResult result) { throw new UnsupportedOperationException("TODO: complete result"); }
}
