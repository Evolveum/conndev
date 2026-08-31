/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.logging;

import com.evolveum.polygon.conndev.concepts.DevelopmentMode;
import com.evolveum.polygon.conndev.devtools.log.ConndevLogFormat;

import java.util.List;

/**
 * Safety rails applied to logged protocol data (request/response bodies, SQL).
 *
 * @param maxBodyLength   maximum length of a body before it is truncated, in characters
 * @param redactSensitive whether values of sensitive fields are masked
 * @param sensitiveNames  names of fields considered sensitive (case-insensitive)
 */
public record LogOptions(int maxBodyLength, boolean redactSensitive, List<String> sensitiveNames) {

    /** Normalizes the values: non-positive length falls back to the default, null names to none. */
    public LogOptions {
        if (maxBodyLength <= 0) {
            maxBodyLength = ConndevLogFormat.DEFAULT_MAX_BODY_LENGTH;
        }
        if (sensitiveNames == null) {
            sensitiveNames = List.of();
        }
    }

    /**
     * Production defaults: truncation enabled, sensitive values redacted.
     *
     * @return the options
     */
    public static LogOptions production() {
        return new LogOptions(ConndevLogFormat.DEFAULT_MAX_BODY_LENGTH, true, ConndevLogFormat.DEFAULT_SENSITIVE_NAMES);
    }

    /**
     * Development defaults: truncation enabled, sensitive values left unredacted (debugging
     * authentication issues requires seeing the actual values).
     *
     * @return the options
     */
    public static LogOptions development() {
        return new LogOptions(ConndevLogFormat.DEFAULT_MAX_BODY_LENGTH, false, ConndevLogFormat.DEFAULT_SENSITIVE_NAMES);
    }

    /**
     * Returns the defaults matching the current development mode of this thread.
     *
     * @return the options
     */
    public static LogOptions defaults() {
        return DevelopmentMode.isEnabled() ? development() : production();
    }
}
