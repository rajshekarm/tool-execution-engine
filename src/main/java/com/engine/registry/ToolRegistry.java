package com.engine.registry;

import com.engine.model.*;
import java.util.Optional;
public interface ToolRegistry {
    void register(String id, Tool tool);
    Optional<Tool> resolve(String id);
}
