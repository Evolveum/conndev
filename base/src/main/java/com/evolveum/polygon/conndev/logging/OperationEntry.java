/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.logging;

import com.evolveum.polygon.conndev.logging.protocol.HttpProtocolData;
import com.evolveum.polygon.conndev.logging.protocol.ProtocolData;
import com.evolveum.polygon.conndev.logging.protocol.SqlProtocolData;

import java.util.Map;

/**
 * A chainable operation entry: a running ConnId operation whose individual events (protocol
 * data, details, result) are logged as separate lines sharing the same entry id, so the entry
 * can be reconstructed afterwards from the log lines.
 *
 * <p>In development mode every event is emitted as a structured log line; otherwise protocol
 * events are not emitted at all and the remaining events fall back to regular
 * {@code info}/{@code error} lines.
 */
public interface OperationEntry {

    /**
     * Returns the unique identifier of the entry, shared by all of its log lines.
     *
     * @return the entry id
     */
    String id();

    /**
     * Logs generic protocol data.
     *
     * @param data the protocol data
     * @return this entry
     */
    OperationEntry protocol(ProtocolData data);

    /**
     * Logs an outgoing HTTP request.
     *
     * @param request the request data
     * @return this entry
     */
    OperationEntry http(HttpProtocolData.Request request);

    /**
     * Logs a received HTTP response.
     *
     * @param response the response data
     * @return this entry
     */
    OperationEntry http(HttpProtocolData.Response response);

    /**
     * Logs an executed SQL query.
     *
     * @param query the query data
     * @return this entry
     */
    OperationEntry sql(SqlProtocolData.Query query);

    /**
     * Logs a key/value detail.
     *
     * @param key   the detail key
     * @param value the detail value
     * @return this entry
     */
    OperationEntry detail(String key, Object value);

    /**
     * Logs multiple key/value details.
     *
     * @param kvs the detail data
     * @return this entry
     */
    OperationEntry detail(Map<String, Object> kvs);

    /**
     * Logs the successful end result and completes the entry.
     *
     * @param result the result value
     * @return this entry
     */
    OperationEntry result(Object result);

    /**
     * Logs a failure and completes the entry. In non-dev mode falls back to a regular error
     * line carrying the message and the stack trace.
     *
     * @param message   the failure message
     * @param throwable the failure (may be null)
     */
    void error(String message, Throwable throwable);
}
