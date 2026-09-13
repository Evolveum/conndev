 /*
 * Copyright (c) 2026 Evolveum and contributors
 * 
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 * 
 */
package com.evolveum.polygon.conndev.logging;

import com.evolveum.polygon.conndev.concepts.SourceLocation;
import com.evolveum.polygon.conndev.devtools.log.*;
import com.evolveum.polygon.conndev.logging.protocol.ProtocolData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

class DevelopmentModeLogWriter implements Slf4JLogWriter {

    private static final ObjectMapper JSON = new ObjectMapper();

    private static final Logger LOG = LoggerFactory.getLogger(Slf4JConnDevLog.class);

    private static final String MASKED_VALUE = "****";


    @Override
    public void logStandalone(Logger logger, LogSeverity severity, String message, Throwable throwable) {
        EventType eventType = severity == LogSeverity.ERROR ? EventType.ERROR : EventType.MESSAGE;
        ErrorPayload errorPayload = severity == LogSeverity.ERROR ? new ErrorPayload(message, stackTraceLines(throwable)) : null;
        emit(logger, severity, new StructuredLogEvent(ConndevLogFormat.VERSION, null, null, null, System.currentTimeMillis(),
                severity, Thread.currentThread().getName(), eventType, null, null, message,
                SourceLocation.determineForLogging().toString(), null, null, null, errorPayload));

    }

    @Override
    public void emitEntryEvent(Logger logger, OperationEntryState state, EventType eventType, LogSeverity severity, String message, SourceLocation location) {
        var event = new StructuredLogEvent(ConndevLogFormat.VERSION, state.id(), state.parentId(),
                state.nextSequence(), System.currentTimeMillis(), severity, Thread.currentThread().getName(),
                eventType, state.operation(), state.objectClass(), message, location != null ? location.toString() : null, null, null, null, null);
        logger.info(structured(event));
    }

    @Override
    public void emitDetail(Logger logger, OperationEntryState state, String rendered, Map<String, Object> kvs) {
        var event = entryEvent(state, EventType.DETAIL, LogSeverity.DEBUG, rendered,
                SourceLocation.determineForLogging(), kvs, null, null, null);
        logger.debug(structured(event));
    }

    @Override
    public void emitProtocol(Logger logger, OperationEntryState state, LogOptions options, ProtocolData data) {
        var fields = data.fields() == null ? Map.<String, Object>of() : data.fields();
        var sql = fields.get("sql") instanceof String s ? truncate(s, options) : null;
        var payload = new ProtocolPayload(
                data.type(),
                data.kind(),
                fields.get("method") instanceof String s ? s : null,
                fields.get("uri") instanceof String s ? s : null,
                fields.get("status") instanceof Number n ? n.intValue() : null,
                renderBody(fields.get("body"), options),
                sql,
                fields.get("params") instanceof Map<?, ?> m ? castParams(m) : null);
        emitProtocol(logger, state, () -> payload, "Protocol " + data.type() + " " + data.kind());
    }

    @Override
    public void emitResult(Logger logger, OperationEntryState state, String rendered, Object result) {
        var event = entryEvent(state, EventType.RESULT, LogSeverity.INFO, rendered,
                SourceLocation.determineForLogging(), null, null, new ResultPayload(true, rendered), null);
        logger.info(structured(event));
    }

    @Override
    public void emitProtocol(Logger logger, OperationEntryState state, Supplier<ProtocolPayload> payloadSupplier, String message) {
        var payload = payloadSupplier.get();
        if (!ConndevLogFormat.PROTOCOL_HTTP.equals(payload.type()) || !hasBody(payload.body())) {
            emitProtocolEvent(logger, state, payload, message, LogSeverity.DEBUG);
            return;
        }
        var skeleton = new ProtocolPayload(payload.type(), payload.kind(), payload.method(),
                payload.uri(), payload.status(), null, null, null);
        emitProtocolEvent(logger, state, skeleton, message, LogSeverity.DEBUG);
        var bodyKind = ConndevLogFormat.HTTP_REQUEST.equals(payload.kind())
                ? ConndevLogFormat.HTTP_REQUEST_BODY
                : ConndevLogFormat.HTTP_RESPONSE_BODY;
        var bodyPayload = new ProtocolPayload(payload.type(), bodyKind, payload.method(), payload.uri(),
                payload.status(), payload.body(), null, null);
        emitProtocolEvent(logger, state, bodyPayload, bodyMessage(bodyKind, payload), LogSeverity.TRACE);
    }

    @Override
    public void emitError(Logger logger, OperationEntryState state, String message, Throwable throwable) {
        var event = entryEvent(state, EventType.ERROR, LogSeverity.ERROR, message,
                SourceLocation.determineForLogging(), null, null, null,
                new ErrorPayload(message, stackTraceLines(throwable)));
        logger.error(structured(event));
    }

    private void emit(Logger logger, LogSeverity severity, StructuredLogEvent event) {
        var message = structured(event);
        switch (severity) {
            case TRACE -> logger.trace(message);
            case DEBUG -> logger.debug(message);
            case INFO -> logger.info(message);
            case WARN -> logger.warn(message);
            case ERROR -> logger.error(message);
        }
    }

    private void emitProtocolEvent(Logger logger, OperationEntryState state, ProtocolPayload payload, String message,
                                   LogSeverity severity) {
        var event = entryEvent(state, EventType.PROTOCOL, severity, message,
                SourceLocation.determineForLogging(), null, payload, null, null);
        emit(logger, severity, event);

    }

    
    
    
    private static String bodyMessage(String bodyKind, ProtocolPayload payload) {
        return ConndevLogFormat.HTTP_REQUEST_BODY.equals(bodyKind)
                ? "HTTP request body " + payload.uri()
                : "HTTP response body " + payload.status() + " " + payload.uri();
    }

    private static boolean hasBody(String body) {
        return body != null && !body.isEmpty();
    }

    // ========================================================================
    // Event construction and rendering
    // ========================================================================

    private static StructuredLogEvent entryEvent(OperationEntryState state, EventType type, LogSeverity severity,
                                                 String message, SourceLocation location, Map<String, Object> detail,
                                                 ProtocolPayload protocol, ResultPayload result, ErrorPayload error) {
        return new StructuredLogEvent(ConndevLogFormat.VERSION, state.id(), state.parentId(),
                state.nextSequence(), System.currentTimeMillis(), severity, Thread.currentThread().getName(),
                type, state.operation(), state.objectClass(), message, location != null ? location.toString() : null, detail, protocol, result, error);
    }

    public static String structured(StructuredLogEvent event) {
        return ConndevLogFormat.formatLine(event.message(), toJson(event));
    }


    // ========================================================================
    // Body safety rails
    // ========================================================================

    static String renderBody(Object body, LogOptions options) {
        if (body == null) {
            return null;
        }
        var text = body instanceof String s ? s : toJson(body);
        return truncate(redact(text, options), options);
    }

    private static String redact(String body, LogOptions options) {
        if (!options.redactSensitive() || body == null || options.sensitiveNames().isEmpty()) {
            return body;
        }
        var trimmed = body.trim();
        if (!trimmed.startsWith("{") && !trimmed.startsWith("[")) {
            return body;
        }
        try {
            var node = JSON.readTree(body);
            redactNode(node, options);
            return JSON.writeValueAsString(node);
        } catch (Exception e) {
            return body;
        }
    }

    private static void redactNode(JsonNode node, LogOptions options) {
        if (node.isObject()) {
            var object = (ObjectNode) node;
            for (var entry : object.properties()) {
                var name = entry.getKey();
                var value = entry.getValue();
                if (isSensitive(name, options)) {
                    object.put(name, MASKED_VALUE);
                } else if (value != null && !value.isValueNode()) {
                    redactNode(value, options);
                }
            }
        } else if (node.isArray()) {
            for (var element : node) {
                redactNode(element, options);
            }
        }
    }

    private static boolean isSensitive(String name, LogOptions options) {
        for (var sensitive : options.sensitiveNames()) {
            if (sensitive.equalsIgnoreCase(name)) {
                return true;
            }
        }
        return false;
    }

    static String truncate(String body, LogOptions options) {
        var max = options.maxBodyLength();
        if (body == null || body.length() <= max) {
            return body;
        }
        return body.substring(0, max) + "…(truncated, " + body.length() + " chars total)";
    }


    private static List<String> stackTraceLines(Throwable throwable) {
        if (throwable == null) {
            return null;
        }
        var writer = new StringWriter();
        throwable.printStackTrace(new PrintWriter(writer));
        return Arrays.asList(writer.toString().split("\\R"));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castParams(Map<?, ?> params) {
        return (Map<String, Object>) params;
    }

    public static String toJson(Object value) {
        try {
            return JSON.writeValueAsString(value);
        } catch (Exception e) {
            LOG.warn("Failed to serialize log event", e);
            return "{}";
        }
    }

}
