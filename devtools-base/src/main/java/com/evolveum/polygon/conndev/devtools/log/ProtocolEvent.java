/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.devtools.log;

/**
 * A protocol event collected while correlating an operation entry.
 *
 * @param ts       event timestamp, epoch millis
 * @param severity event severity
 * @param location caller location of the emitting component
 * @param protocol protocol data
 * @param message  human-readable message
 */
public record ProtocolEvent(long ts, LogSeverity severity, CallerLocation location, ProtocolPayload protocol, String message) {
}
