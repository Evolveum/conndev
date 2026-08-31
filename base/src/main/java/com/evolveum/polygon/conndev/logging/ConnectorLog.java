/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.logging;

import com.evolveum.polygon.conndev.concepts.CheckedCallable;
import com.evolveum.polygon.conndev.concepts.DevelopmentMode;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Logging facade for connector operations.
 *
 * <p>All connector logging flows through this facade. When {@link DevelopmentMode} is enabled
 * the events are emitted as structured log lines (see
 * {@link com.evolveum.polygon.conndev.devtools.log.ConndevLogFormat ConndevLogFormat}),
 * otherwise they fall back to regular {@code info}/{@code error} lines — protocol data is
 * emitted only in development mode.
 *
 * <p>Instances are bound to an SLF4J logger, typically of the component emitting the events:
 * {@code ConnectorLog.of(RestClient.class)}.
 */
public interface ConnectorLog {

    /**
     * Creates a facade bound to the given logger with the default options.
     *
     * @param logger the SLF4J logger
     * @return the facade
     */
    static ConnectorLog of(Logger logger) {
        return of(logger, null);
    }

    /**
     * Creates a facade bound to the given logger.
     *
     * @param logger  the SLF4J logger
     * @param options the logging options (null for defaults)
     * @return the facade
     */
    static ConnectorLog of(Logger logger, LogOptions options) {
        return new Slf4jConnectorLog(logger, options);
    }

    /**
     * Creates a facade bound to a logger of the given class with the default options.
     *
     * @param owner the class the logger is bound to
     * @return the facade
     */
    static ConnectorLog of(Class<?> owner) {
        return of(LoggerFactory.getLogger(owner));
    }

    /**
     * Creates a facade bound to a logger of the given class.
     *
     * @param owner   the class the logger is bound to
     * @param options the logging options (null for defaults)
     * @return the facade
     */
    static ConnectorLog of(Class<?> owner, LogOptions options) {
        return of(LoggerFactory.getLogger(owner), options);
    }

    /**
     * Starts a new operation entry and registers it as the current entry of this thread.
     *
     * @param operation   the ConnId operation name, e.g. {@code search}, {@code create}
     * @param objectClass the object class the operation applies to
     * @param message     human-readable description of the operation
     * @return the new entry
     */
    OperationEntry operation(String operation, ObjectClass objectClass, String message);

    /**
     * Starts a new operation entry and registers it as the current entry of this thread.
     *
     * @param operation   the ConnId operation name
     * @param objectClass the object class name
     * @param message     human-readable description of the operation
     * @return the new entry
     */
    default OperationEntry operation(String operation, String objectClass, String message) {
        return operation(operation, objectClass == null ? null : new ObjectClass(objectClass), message);
    }

    /**
     * Returns the operation entry currently being executed on this thread, if any.
     *
     * @return the current entry, or null if there is no active entry
     */
    OperationEntry currentOperation();

    /**
     * Logs a debug message (structured line with caller location in development mode).
     *
     * @param message the message
     */
    void debug(String message);

    /**
     * Logs an info message (structured line with caller location in development mode).
     *
     * @param message the message
     */
    void info(String message);

    /**
     * Logs a warning message (structured line with caller location in development mode).
     *
     * @param message the message
     */
    void warn(String message);

    /**
     * Logs an error message (structured line in development mode, regular error line with the
     * stack trace otherwise).
     *
     * @param message   the message
     * @param throwable the error (may be null)
     */
    void error(String message, Throwable throwable);

    /**
     * Executes the given work within a new operation entry: logs the operation start, the
     * result (or the failure) and releases the entry state afterwards.
     *
     * @param operation   the ConnId operation name
     * @param objectClass the object class the operation applies to
     * @param message     human-readable description of the operation
     * @param work        the work to execute
     * @param <V>         the result type
     * @param <E>         the exception type the work may throw
     * @return the result of the work
     * @throws E if the work fails
     */
    <V, E extends Throwable> V runOperation(String operation, ObjectClass objectClass, String message,
                                            CheckedCallable<V, E> work) throws E;
}
