package com.engine.registry;

import com.engine.model.*;
import java.util.Optional;
public final class DefaultToolRegistry implements ToolRegistry {
    public void register(String id, Tool tool) { throw new UnsupportedOperationException("TODO: register tool"); }
    public Optional<Tool> resolve(String id) { throw new UnsupportedOperationException("TODO: resolve tool"); }
}
