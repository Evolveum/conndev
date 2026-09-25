/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.logging;

import com.evolveum.polygon.conndev.concepts.DevelopmentMode;
import com.evolveum.polygon.conndev.concepts.SourceLocation;
import com.evolveum.polygon.conndev.devtools.log.EventType;
import com.evolveum.polygon.conndev.devtools.log.LogSeverity;
import com.evolveum.polygon.conndev.devtools.log.ProtocolPayload;
import com.evolveum.polygon.conndev.logging.protocol.ProtocolData;
import org.identityconnectors.common.logging.Log;

import java.util.Map;
import java.util.function.Supplier;

interface ConnIdLogWriter extends DevelopmentMode.SpecificImplementation {

    void logStandalone(Log log, Class<?> owner, LogSeverity logSeverity, String message, Throwable throwable);

    void emitEntryEvent(Log log, Class<?> owner, OperationEntryState state, EventType eventType, LogSeverity severity, String message, SourceLocation location);

    void emitDetail(Log log, Class<?> owner, OperationEntryState state, String rendered, Map<String, Object> kvs);

    void emitProtocol(Log log, Class<?> owner, OperationEntryState state, LogOptions options, ProtocolData data);

    void emitResult(Log log, Class<?> owner, OperationEntryState state, String rendered, Object result);

    void emitProtocol(Log log, Class<?> owner, OperationEntryState state, Supplier<ProtocolPayload> payload, String message);

    void emitError(Log log, Class<?> owner, OperationEntryState state, String message, Throwable throwable);

    static Log.Level toConnIdLevel(LogSeverity severity) {
        return switch (severity) {
            case TRACE, DEBUG -> Log.Level.OK;
            case INFO -> Log.Level.INFO;
            case WARN -> Log.Level.WARN;
            case ERROR -> Log.Level.ERROR;
        };
    }
}
