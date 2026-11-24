package com.engine.model;

import java.util.Map;
@FunctionalInterface
public interface Tool {
    Object invoke(Map<String, Object> arguments) throws Exception;
}
