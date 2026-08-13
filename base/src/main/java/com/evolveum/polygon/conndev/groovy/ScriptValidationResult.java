/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.groovy;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Outcome of validating a candidate (or a set of dependent) Groovy scripts: either {@link
 * Status#OK}, or {@link Status#ERROR} with one failure per affected script in {@link #errors}.
 * {@link #combined} tracks whether this is an aggregation of several checks (via {@link
 * #combined(List)}) so that shape stays {@code {"status":"error","errors":[...]}} even when only
 * one of the checks actually failed — callers of a multi-script check always get the same shape,
 * regardless of how many failed.
 */
public record ScriptValidationResult(Status status, List<ScriptError> errors, boolean combined) {

    public enum Status {
        OK, ERROR
    }

    public static ScriptValidationResult ok() {
        return new ScriptValidationResult(Status.OK, List.of(), false);
    }

    public static ScriptValidationResult error(ScriptError error) {
        return new ScriptValidationResult(Status.ERROR, List.of(error), false);
    }

    public static ScriptValidationResult combined(List<ScriptError> errors) {
        return errors.isEmpty() ? ok() : new ScriptValidationResult(Status.ERROR, errors, true);
    }

    public Map<String, Object> toMap() {
        if (status == Status.OK) {
            return Map.of("status", "ok");
        }
        if (!combined) {
            return errors.get(0).toMap();
        }
        var map = new LinkedHashMap<String, Object>();
        map.put("status", "error");
        map.put("errors", errors.stream().map(ScriptError::toMap).toList());
        return map;
    }
}
