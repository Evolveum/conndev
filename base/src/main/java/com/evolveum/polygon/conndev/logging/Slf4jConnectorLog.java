/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.logging;

import com.evolveum.polygon.conndev.concepts.CheckedCallable;
import com.evolveum.polygon.conndev.concepts.DevelopmentMode;
import com.evolveum.polygon.conndev.devtools.log.CallerLocation;
import com.evolveum.polygon.conndev.devtools.log.ConndevLogFormat;
import com.evolveum.polygon.conndev.devtools.log.ErrorPayload;
import com.evolveum.polygon.conndev.devtools.log.EventType;
import com.evolveum.polygon.conndev.devtools.log.LogSeverity;
import com.evolveum.polygon.conndev.devtools.log.ProtocolPayload;
import com.evolveum.polygon.conndev.devtools.log.ResultPayload;
import com.evolveum.polygon.conndev.devtools.log.StructuredLogEvent;
import com.evolveum.polygon.conndev.logging.protocol.HttpProtocolData;
import com.evolveum.polygon.conndev.logging.protocol.ProtocolData;
import com.evolveum.polygon.conndev.logging.protocol.SqlProtocolData;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.slf4j.Logger;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * SLF4J-backed {@link ConnectorLog} implementation.
 *
 * <p>Every event is routed through this class: in development mode it is emitted as a
 * structured log line (human-readable message, then the format marker and the JSON payload),
 * otherwise as a regular SLF4J line — protocol events are dropped entirely.
 */
public final class Slf4jConnectorLog implements ConnectorLog {

    private static final ObjectMapper JSON = new ObjectMapper();

    private static final String MASKED_VALUE = "•••••";

    private final Logger logger;
    private final LogOptions options;

    public Slf4jConnectorLog(Logger logger, LogOptions options) {
        this.logger = logger;
        this.options = options == null ? LogOptions.defaults() : options;
    }

    /**
     * Returns the bound SLF4J logger.
     *
     * @return the logger
     */
    public Logger logger() {
        return logger;
    }

    /**
     * Returns the logging options in effect.
     *
     * @return the options
     */
    public LogOptions options() {
        return options;
    }

    // ========================================================================
    // Operation entries
    // ========================================================================

    @Override
    public OperationEntry operation(String operation, ObjectClass objectClass, String message) {
        var parent = OperationEntryContext.current();
        var state = new OperationEntryState(
                parent == null ? null : parent.id(),
                parent,
                operation,
                objectClass == null ? null : objectClass.getObjectClassValue(),
                CallerLocationCapture.capture(),
                System.currentTimeMillis());
        OperationEntryContext.push(state);
        var entry = new OperationEntryImpl(this, state);
        emitEntryEvent(state, EventType.OPERATION, LogSeverity.INFO, message, state.location());
        return entry;
    }

    @Override
    public OperationEntry currentOperation() {
        var state = OperationEntryContext.current();
        return state == null || state.isCompleted() ? null : new OperationEntryImpl(this, state);
    }

    @Override
    public <V, E extends Throwable> V runOperation(String operation, ObjectClass objectClass, String message,
                                                   CheckedCallable<V, E> work) throws E {
        var entry = operation(operation, objectClass, message);
        try {
            var result = work.call();
            entry.result(result);
            return result;
        } catch (Throwable t) {
            entry.error(t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage(), t);
            throw t;
        }
    }

    // ========================================================================
    // Standalone messages
    // ========================================================================

    @Override
    public void debug(String message) {
        logStandalone(LogSeverity.DEBUG, message, null);
    }

    @Override
    public void info(String message) {
        logStandalone(LogSeverity.INFO, message, null);
    }

    @Override
    public void warn(String message) {
        logStandalone(LogSeverity.WARN, message, null);
    }

    @Override
    public void error(String message, Throwable throwable) {
        logStandalone(LogSeverity.ERROR, message, throwable);
    }

    private void logStandalone(LogSeverity severity, String message, Throwable throwable) {
        if (!DevelopmentMode.isEnabled()) {
            switch (severity) {
                case DEBUG -> logger.debug(message);
                case INFO -> logger.info(message);
                case WARN -> logger.warn(message);
                case ERROR -> logger.error(message, throwable);
            }
            return;
        }
        var event = severity == LogSeverity.ERROR
                ? new StructuredLogEvent(ConndevLogFormat.VERSION, null, null, null, System.currentTimeMillis(),
                        severity, Thread.currentThread().getName(), EventType.ERROR, null, null, message,
                        CallerLocationCapture.capture(), null, null, null,
                        new ErrorPayload(message, stackTraceLines(throwable)))
                : new StructuredLogEvent(ConndevLogFormat.VERSION, null, null, null, System.currentTimeMillis(),
                        severity, Thread.currentThread().getName(), EventType.MESSAGE, null, null, message,
                        CallerLocationCapture.capture(), null, null, null, null);
        switch (severity) {
            case DEBUG -> logger.debug(structured(event));
            case INFO -> logger.info(structured(event));
            case WARN -> logger.warn(structured(event));
            case ERROR -> logger.error(structured(event));
        }
    }

    // ========================================================================
    // Entry events (called from OperationEntryImpl)
    // ========================================================================

    void emitDetail(OperationEntryState state, Map<String, Object> kvs) {
        var rendered = renderDetail(kvs);
        if (!DevelopmentMode.isEnabled()) {
            logger.info(rendered);
            return;
        }
        var event = entryEvent(state, EventType.DETAIL, LogSeverity.INFO, rendered,
                CallerLocationCapture.capture(), kvs, null, null, null);
        logger.info(structured(event));
    }

    void emitHttp(OperationEntryState state, HttpProtocolData.Request request) {
        emitProtocol(state, new ProtocolPayload(
                        ConndevLogFormat.PROTOCOL_HTTP, ConndevLogFormat.HTTP_REQUEST,
                        request.method(), request.uri(), null, renderBody(request.body()), null, null),
                "HTTP " + request.method() + " " + request.uri());
    }

    void emitHttpResponse(OperationEntryState state, HttpProtocolData.Response response) {
        emitProtocol(state, new ProtocolPayload(
                        ConndevLogFormat.PROTOCOL_HTTP, ConndevLogFormat.HTTP_RESPONSE,
                        null, response.uri(), response.status(), renderBody(response.body()), null, null),
                "HTTP response " + response.status() + " " + response.uri());
    }

    void emitSql(OperationEntryState state, SqlProtocolData.Query query) {
        emitProtocol(state, new ProtocolPayload(
                        ConndevLogFormat.PROTOCOL_SQL, ConndevLogFormat.SQL_QUERY,
                        null, null, null, null, truncate(query.sql()), query.params()),
                "SQL query");
    }

    void emitProtocol(OperationEntryState state, ProtocolData data) {
        var fields = data.fields() == null ? Map.<String, Object>of() : data.fields();
        var sql = fields.get("sql") instanceof String s ? truncate(s) : null;
        var payload = new ProtocolPayload(
                data.type(),
                data.kind(),
                fields.get("method") instanceof String s ? s : null,
                fields.get("uri") instanceof String s ? s : null,
                fields.get("status") instanceof Number n ? n.intValue() : null,
                renderBody(fields.get("body")),
                sql,
                fields.get("params") instanceof Map<?, ?> m ? castParams(m) : null);
        emitProtocol(state, payload, "Protocol " + data.type() + " " + data.kind());
    }

    private void emitProtocol(OperationEntryState state, ProtocolPayload payload, String message) {
        if (!DevelopmentMode.isEnabled()) {
            return;
        }
        var event = entryEvent(state, EventType.PROTOCOL, LogSeverity.DEBUG, message,
                CallerLocationCapture.capture(), null, payload, null, null);
        logger.debug(structured(event));
    }

    void emitResult(OperationEntryState state, Object result) {
        state.markCompleted();
        OperationEntryContext.restore(state);
        var rendered = renderValue(result);
        if (!DevelopmentMode.isEnabled()) {
            logger.info(plainCompleted(state, rendered));
            return;
        }
        var event = entryEvent(state, EventType.RESULT, LogSeverity.INFO, rendered,
                CallerLocationCapture.capture(), null, null, new ResultPayload(true, rendered), null);
        logger.info(structured(event));
    }

    void emitError(OperationEntryState state, String message, Throwable throwable) {
        state.markCompleted();
        OperationEntryContext.restore(state);
        if (!DevelopmentMode.isEnabled()) {
            logger.error(plainFailed(state, message), throwable);
            return;
        }
        var event = entryEvent(state, EventType.ERROR, LogSeverity.ERROR, message,
                CallerLocationCapture.capture(), null, null, null,
                new ErrorPayload(message, stackTraceLines(throwable)));
        logger.error(structured(event));
    }

    private void emitEntryEvent(OperationEntryState state, EventType type, LogSeverity severity, String message,
                                CallerLocation location) {
        if (!DevelopmentMode.isEnabled()) {
            logger.info(plainOperation(state, message));
            return;
        }
        var event = new StructuredLogEvent(ConndevLogFormat.VERSION, state.id(), state.parentId(),
                state.nextSequence(), System.currentTimeMillis(), severity, Thread.currentThread().getName(),
                type, state.operation(), state.objectClass(), message, location, null, null, null, null);
        logger.info(structured(event));
    }

    // ========================================================================
    // Event construction and rendering
    // ========================================================================

    private StructuredLogEvent entryEvent(OperationEntryState state, EventType type, LogSeverity severity,
                                          String message, CallerLocation location, Map<String, Object> detail,
                                          ProtocolPayload protocol, ResultPayload result, ErrorPayload error) {
        return new StructuredLogEvent(ConndevLogFormat.VERSION, state.id(), state.parentId(),
                state.nextSequence(), System.currentTimeMillis(), severity, Thread.currentThread().getName(),
                type, state.operation(), state.objectClass(), message, location, detail, protocol, result, error);
    }

    private String structured(StructuredLogEvent event) {
        return ConndevLogFormat.formatLine(event.message(), toJson(event));
    }

    private String toJson(Object value) {
        try {
            return JSON.writeValueAsString(value);
        } catch (Exception e) {
            logger.warn("Failed to serialize log event", e);
            return "{}";
        }
    }

    private static String plainOperation(OperationEntryState state, String message) {
        return state.operation() + " on " + state.objectClass() + ": " + message;
    }

    private static String plainCompleted(OperationEntryState state, String rendered) {
        return rendered == null
                ? state.operation() + " on " + state.objectClass() + " completed"
                : state.operation() + " on " + state.objectClass() + " completed: " + rendered;
    }

    private static String plainFailed(OperationEntryState state, String message) {
        return state.operation() + " on " + state.objectClass() + " failed: " + message;
    }

    static String renderDetail(Map<String, Object> kvs) {
        var sb = new StringBuilder("detail");
        if (kvs != null) {
            kvs.forEach((key, value) -> sb.append(' ').append(key).append('=').append(value));
        }
        return sb.toString();
    }

    private static String renderValue(Object value) {
        return value == null ? null : String.valueOf(value);
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

    // ========================================================================
    // Body safety rails
    // ========================================================================

    private String renderBody(Object body) {
        if (body == null) {
            return null;
        }
        var text = body instanceof String s ? s : toJson(body);
        return truncate(redact(text));
    }

    private String redact(String body) {
        if (!options.redactSensitive() || body == null || options.sensitiveNames().isEmpty()) {
            return body;
        }
        var trimmed = body.trim();
        if (!trimmed.startsWith("{") && !trimmed.startsWith("[")) {
            return body;
        }
        try {
            var node = JSON.readTree(body);
            redactNode(node);
            return JSON.writeValueAsString(node);
        } catch (Exception e) {
            return body;
        }
    }

    private void redactNode(JsonNode node) {
        if (node.isObject()) {
            var object = (ObjectNode) node;
            for (var entry : object.properties()) {
                var name = entry.getKey();
                var value = entry.getValue();
                if (isSensitive(name)) {
                    object.put(name, MASKED_VALUE);
                } else if (value != null && !value.isValueNode()) {
                    redactNode(value);
                }
            }
        } else if (node.isArray()) {
            for (var element : node) {
                redactNode(element);
            }
        }
    }

    private boolean isSensitive(String name) {
        for (var sensitive : options.sensitiveNames()) {
            if (sensitive.equalsIgnoreCase(name)) {
                return true;
            }
        }
        return false;
    }

    private String truncate(String body) {
        var max = options.maxBodyLength();
        if (body == null || body.length() <= max) {
            return body;
        }
        return body.substring(0, max) + "…(truncated, " + body.length() + " chars total)";
    }
}
