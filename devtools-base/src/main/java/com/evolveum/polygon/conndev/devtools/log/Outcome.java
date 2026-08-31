/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.devtools.log;

import java.util.List;

/**
 * End result of a correlated operation entry.
 *
 * @param ok         whether the operation succeeded
 * @param value      result value (successful operations)
 * @param message    failure message (failed operations)
 * @param stacktrace failure stack trace lines (failed operations)
 */
public record Outcome(boolean ok, Object value, String message, List<String> stacktrace) {

    /**
     * Creates a successful outcome.
     *
     * @param value the result value
     * @return the outcome
     */
    public static Outcome success(Object value) {
        return new Outcome(true, value, null, null);
    }

    /**
     * Creates a failed outcome.
     *
     * @param message    failure message
     * @param stacktrace failure stack trace lines
     * @return the outcome
     */
    public static Outcome failure(String message, List<String> stacktrace) {
        return new Outcome(false, null, message, stacktrace);
    }
}
