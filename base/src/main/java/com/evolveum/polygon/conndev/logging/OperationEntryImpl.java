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

import java.util.HashMap;
import java.util.Map;

/** Default {@link OperationEntry} implementation delegating emission to a {@link Slf4jConnectorLog}. */
public final class OperationEntryImpl implements OperationEntry {

    private final Slf4jConnectorLog log;
    private final OperationEntryState state;

    OperationEntryImpl(Slf4jConnectorLog log, OperationEntryState state) {
        this.log = log;
        this.state = state;
    }

    @Override
    public String id() {
        return state.id();
    }

    @Override
    public OperationEntry protocol(ProtocolData data) {
        log.emitProtocol(state, data);
        return this;
    }

    @Override
    public OperationEntry http(HttpProtocolData.Request request) {
        log.emitHttp(state, request);
        return this;
    }

    @Override
    public OperationEntry http(HttpProtocolData.Response response) {
        log.emitHttpResponse(state, response);
        return this;
    }

    @Override
    public OperationEntry sql(SqlProtocolData.Query query) {
        log.emitSql(state, query);
        return this;
    }

    @Override
    public OperationEntry detail(String key, Object value) {
        var kvs = new HashMap<String, Object>();
        kvs.put(key, value);
        return detail(kvs);
    }

    @Override
    public OperationEntry detail(Map<String, Object> kvs) {
        log.emitDetail(state, kvs);
        return this;
    }

    @Override
    public OperationEntry result(Object result) {
        log.emitResult(state, result);
        return this;
    }

    @Override
    public void error(String message, Throwable throwable) {
        log.emitError(state, message, throwable);
    }
}
