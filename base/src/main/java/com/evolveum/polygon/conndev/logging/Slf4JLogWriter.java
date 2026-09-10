package com.evolveum.polygon.conndev.logging;

import com.evolveum.polygon.conndev.concepts.DevelopmentMode;
import com.evolveum.polygon.conndev.concepts.SourceLocation;
import com.evolveum.polygon.conndev.devtools.log.EventType;
import com.evolveum.polygon.conndev.devtools.log.LogSeverity;
import com.evolveum.polygon.conndev.devtools.log.ProtocolPayload;
import com.evolveum.polygon.conndev.logging.protocol.ProtocolData;
import org.slf4j.Logger;

import java.util.Map;
import java.util.function.Supplier;

interface Slf4JLogWriter extends DevelopmentMode.SpecificImplementation {

    void logStandalone(Logger logger, LogSeverity logSeverity, String message, Throwable throwable);

    void emitEntryEvent(Logger logger, OperationEntryState state, EventType eventType, LogSeverity severity, String message, SourceLocation location);

    void emitDetail(Logger logger, OperationEntryState state, String rendered, Map<String, Object> kvs);

    void emitProtocol(Logger logger, OperationEntryState state, LogOptions options, ProtocolData data);

    void emitResult(Logger logger, OperationEntryState state, String rendered, Object result);

    void emitProtocol(Logger logger, OperationEntryState state, Supplier<ProtocolPayload> payload, String message);

    void emitError(Logger logger, OperationEntryState state, String message, Throwable throwable);
}
