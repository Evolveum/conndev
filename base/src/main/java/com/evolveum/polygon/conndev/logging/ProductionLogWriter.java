/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.logging;

import com.evolveum.polygon.conndev.concepts.SourceLocation;
import com.evolveum.polygon.conndev.devtools.log.EventType;
import com.evolveum.polygon.conndev.devtools.log.LogSeverity;
import com.evolveum.polygon.conndev.devtools.log.ProtocolPayload;
import com.evolveum.polygon.conndev.logging.protocol.ProtocolData;
import org.identityconnectors.common.logging.Log;

import java.util.Map;
import java.util.function.Supplier;

class ProductionLogWriter implements ConnIdLogWriter {

    @Override
    public void logStandalone(Log log, Class<?> owner, LogSeverity severity, String message, Throwable throwable) {
        log.log(owner, null, ConnIdLogWriter.toConnIdLevel(severity), message,
                severity == LogSeverity.ERROR ? throwable : null);
    }

    @Override
    public void emitEntryEvent(Log log, Class<?> owner, OperationEntryState state, EventType eventType, LogSeverity severity, String message, SourceLocation location) {
        log.log(owner, null, Log.Level.INFO, plainOperation(state, message), null);
    }

    @Override
    public void emitDetail(Log log, Class<?> owner, OperationEntryState state, String rendered, Map<String, Object> kvs) {
        log.log(owner, null, Log.Level.OK, rendered, null);
    }

    @Override
    public void emitProtocol(Log log, Class<?> owner, OperationEntryState state, LogOptions options, ProtocolData data) {
        // Noop
    }

    @Override
    public void emitResult(Log log, Class<?> owner, OperationEntryState state, String rendered, Object result) {
        log.log(owner, null, Log.Level.INFO, plainCompleted(state, rendered), null);
    }

    @Override
    public void emitProtocol(Log log, Class<?> owner, OperationEntryState state, Supplier<ProtocolPayload> payload, String message) {
        // Noop
    }

    @Override
    public void emitError(Log log, Class<?> owner, OperationEntryState state, String message, Throwable throwable) {
        log.log(owner, null, Log.Level.ERROR, plainFailed(state, message), throwable);
    }

    static String plainOperation(OperationEntryState state, String message) {
        return state.operation() + " on " + state.objectClass() + ": " + message;
    }

    static String plainCompleted(OperationEntryState state, String rendered) {
        return rendered == null
                ? state.operation() + " on " + state.objectClass() + " completed"
                : state.operation() + " on " + state.objectClass() + " completed: " + rendered;
    }

    static String plainFailed(OperationEntryState state, String message) {
        return state.operation() + " on " + state.objectClass() + " failed: " + message;
    }
}
