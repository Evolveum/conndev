/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.groovy;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A single script validation failure, at the given {@link Phase}, with an optional source
 * location and originating script.
 */
public record ScriptError(Phase phase, String message, Integer line, Integer column, String source) {

    public enum Phase {
        COMPILE, EVALUATE, BUILD, INITIALIZATION
    }

    public Map<String, Object> toMap() {
        var map = new LinkedHashMap<String, Object>();
        map.put("status", "error");
        map.put("phase", phase.name().toLowerCase());
        map.put("message", message);
        if (line != null) {
            map.put("line", line);
        }
        if (column != null) {
            map.put("column", column);
        }
        if (source != null) {
            map.put("source", source);
        }
        return map;
    }
}
