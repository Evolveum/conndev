/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.devtools.log;

import java.util.Locale;

/** Log severity levels carried by structured log events. */
public enum LogSeverity {

    TRACE(-1),
    DEBUG(0),
    INFO(1),
    WARN(2),
    ERROR(3);

    private final int rank;

    LogSeverity(int rank) {
        this.rank = rank;
    }

    /**
     * Returns the ordinal rank of the severity (higher is more severe).
     *
     * @return the rank
     */
    public int rank() {
        return rank;
    }

    /**
     * Checks whether this severity is the same as or more severe than the given one.
     *
     * @param other the severity to compare against
     * @return true if this severity is at least as severe as the given one
     */
    public boolean atLeast(LogSeverity other) {
        return other != null && rank >= other.rank;
    }

    /**
     * Looks up a severity by name, case-insensitively.
     *
     * @param name the severity name
     * @return the severity, or null if the name is unknown
     */
    public static LogSeverity fromName(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        try {
            return valueOf(name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
