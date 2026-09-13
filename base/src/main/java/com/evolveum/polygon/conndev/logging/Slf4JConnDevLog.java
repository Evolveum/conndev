/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.logging;

import com.evolveum.polygon.conndev.concepts.CheckedCallable;
import com.evolveum.polygon.conndev.concepts.DevelopmentMode;
import com.evolveum.polygon.conndev.concepts.SourceLocation;
import com.evolveum.polygon.conndev.devtools.log.ConndevLogFormat;
import com.evolveum.polygon.conndev.devtools.log.EventType;
import com.evolveum.polygon.conndev.devtools.log.LogSeverity;
import com.evolveum.polygon.conndev.devtools.log.ProtocolPayload;
import com.evolveum.polygon.conndev.logging.protocol.HttpProtocolData;
import com.evolveum.polygon.conndev.logging.protocol.ProtocolData;
import com.evolveum.polygon.conndev.logging.protocol.SqlProtocolData;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.slf4j.Logger;

import java.util.Map;
import java.util.function.Supplier;

import static com.evolveum.polygon.conndev.logging.DevelopmentModeLogWriter.renderBody;
import static com.evolveum.polygon.conndev.logging.DevelopmentModeLogWriter.truncate;

/**
 * SLF4J-backed {@link ConnDevLog} implementation.
 *
 * <p>Every event is routed through this class: in development mode it is emitted as a
 * structured log line (human-readable message, then the format marker and the JSON payload),
 * otherwise as a regular SLF4J line — protocol events are dropped entirely.
 *
 * <p>When no explicit {@link LogOptions} is configured, the safety rails (truncation,
 * redaction) are resolved at write time against the development mode of the writing thread,
 * so a facade created outside any operation still honors the mode in effect when the line is
 * actually written.
 */
public final class Slf4JConnDevLog implements ConnDevLog {


    private static final Supplier<Slf4JLogWriter> BACKEND = DevelopmentMode.register(Slf4JLogWriter.class, new DevelopmentModeLogWriter(), new ProductionLogWriter());

    private final Logger logger;

    /**
     * Options in effect for this facade: the explicitly configured options if given, otherwise
     * the defaults matching the development mode of the writing thread, resolved per emission.
     */
    private final Supplier<LogOptions> optionsSupplier;

    public Slf4JConnDevLog(Logger logger, LogOptions options) {
        this.logger = logger;
        this.optionsSupplier = options == null ? LogOptions::defaults : () -> options;
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
     * Returns the logging options in effect: the explicitly configured options if given,
     * otherwise the defaults matching the current development mode of this thread.
     *
     * @return the options
     */
    public LogOptions options() {
        return optionsSupplier.get();
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
                SourceLocation.capture(),
                System.currentTimeMillis());
        OperationEntryContext.push(state);
        var entry = new OperationEntryImpl(this, state);
        BACKEND.get().emitEntryEvent(logger, state, EventType.OPERATION, LogSeverity.INFO, message, state.location());
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
        BACKEND.get().logStandalone(logger, LogSeverity.DEBUG, message, null);
    }

    @Override
    public void info(String message) {
        BACKEND.get().logStandalone(logger, LogSeverity.INFO, message, null);
    }

    @Override
    public void warn(String message) {
        BACKEND.get().logStandalone(logger, LogSeverity.WARN, message, null);
    }

    @Override
    public void error(String message, Throwable throwable) {
        BACKEND.get().logStandalone(logger, LogSeverity.ERROR, message, throwable);
    }

    // ========================================================================
    // Entry events (called from OperationEntryImpl)
    // ========================================================================

    void emitDetail(OperationEntryState state, Map<String, Object> kvs) {
        var rendered = renderDetail(kvs);
        BACKEND.get().emitDetail(logger, state, rendered, kvs);
    }

    void emitHttp(OperationEntryState state, HttpProtocolData.Request request) {
        var options = optionsSupplier.get();
        BACKEND.get().emitProtocol(logger, state, () -> new ProtocolPayload(
                        ConndevLogFormat.PROTOCOL_HTTP, ConndevLogFormat.HTTP_REQUEST,
                        request.method(), request.uri(), null, renderBody(request.body(), options), null, null),
                "HTTP " + request.method() + " " + request.uri());
    }

    void emitHttpResponse(OperationEntryState state, HttpProtocolData.Response response) {
        var options = optionsSupplier.get();
        BACKEND.get().emitProtocol(logger, state, () -> new ProtocolPayload(
                        ConndevLogFormat.PROTOCOL_HTTP, ConndevLogFormat.HTTP_RESPONSE,
                        null, response.uri(), response.status(), renderBody(response.body(), options), null, null),
                "HTTP response " + response.status() + " " + response.uri());
    }

    void emitSql(OperationEntryState state, SqlProtocolData.Query query) {
        var options = optionsSupplier.get();
        BACKEND.get().emitProtocol(logger, state, () -> new ProtocolPayload(
                        ConndevLogFormat.PROTOCOL_SQL, ConndevLogFormat.SQL_QUERY,
                    null, null, null, null, truncate(query.sql(), options), query.params()),
            "SQL query");
    }

    void emitProtocol(OperationEntryState state, ProtocolData data) {
        BACKEND.get().emitProtocol(logger, state, optionsSupplier.get(), data);
    }

    void emitResult(OperationEntryState state, Object result) {
        state.markCompleted();
        OperationEntryContext.restore(state);
        var rendered = renderValue(result);
        BACKEND.get().emitResult(logger, state, rendered, result);

    }

    void emitError(OperationEntryState state, String message, Throwable throwable) {
        state.markCompleted();
        OperationEntryContext.restore(state);
        BACKEND.get().emitError(logger, state, message, throwable);
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
}
