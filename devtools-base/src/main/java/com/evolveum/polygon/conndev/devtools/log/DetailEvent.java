/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.devtools.log;

import java.util.Map;

/**
 * A detail event collected while correlating an operation entry.
 *
 * @param ts       event timestamp, epoch millis
 * @param severity event severity
 * @param location caller location of the emitting component
 * @param detail   key/value detail data (may be null for standalone messages)
 * @param message  human-readable message
 */
public record DetailEvent(long ts, LogSeverity severity, CallerLocation location, Map<String, Object> detail, String message) {
}
