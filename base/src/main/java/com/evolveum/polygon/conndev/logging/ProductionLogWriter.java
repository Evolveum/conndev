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
import org.slf4j.Logger;

import java.util.Map;
import java.util.function.Supplier;

class ProductionLogWriter implements Slf4JLogWriter {

    @Override
    public void logStandalone(Logger logger, LogSeverity severity, String message, Throwable throwable) {
        switch (severity) {
            case DEBUG -> logger.debug(message);
            case INFO -> logger.info(message);
            case WARN -> logger.warn(message);
            case ERROR -> logger.error(message, throwable);
            case TRACE -> logger.trace(message);
        }
    }

    @Override
    public void emitEntryEvent(Logger logger, OperationEntryState state, EventType eventType, LogSeverity severity, String message, SourceLocation location) {
        logger.info(plainOperation(state, message));
    }

    @Override
    public void emitDetail(Logger logger, OperationEntryState state, String rendered, Map<String, Object> kvs) {
        logger.debug(rendered);
    }

    @Override
    public void emitProtocol(Logger logger, OperationEntryState state, LogOptions options, ProtocolData data) {
        // Noop
    }

    @Override
    public void emitResult(Logger logger, OperationEntryState state, String rendered, Object result) {
        logger.info(plainCompleted(state, rendered));
    }

    @Override
    public void emitProtocol(Logger logger, OperationEntryState state, Supplier<ProtocolPayload> payload, String message) {
        // Noop
    }

    @Override
    public void emitError(Logger logger, OperationEntryState state, String message, Throwable throwable) {
        logger.error(plainFailed(state, message), throwable);
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
