package com.engine.registry;

import com.engine.model.Tool;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class DefaultToolRegistry
        implements ToolRegistry {

    private final ConcurrentMap<String, Tool> tools =
            new ConcurrentHashMap<>();

    @Override
    public void register(String id, Tool tool) {

        validateId(id);
        Objects.requireNonNull(tool, "tool");

        if (tools.putIfAbsent(id, tool) != null) {
            throw new IllegalArgumentException(
                    "Tool already registered: " + id
            );
        }
    }

    @Override
    public Optional<Tool> resolve(String id) {

        validateId(id);

        return Optional.ofNullable(tools.get(id));
    }

    private static void validateId(String id) {

        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException(
                    "Tool ID must not be null or blank"
            );
        }
    }
}