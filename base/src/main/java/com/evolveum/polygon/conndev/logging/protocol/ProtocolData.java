/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.logging.protocol;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Generic protocol data for protocols without a dedicated type.
 *
 * @param type   protocol type, e.g. {@code http}, {@code sql}
 * @param kind   event kind, e.g. {@code request}, {@code response}, {@code query}
 * @param fields protocol-specific fields ({@code method}, {@code uri}, {@code status},
 *               {@code body}, {@code sql}, {@code params})
 */
public record ProtocolData(String type, String kind, Map<String, Object> fields) {

    /**
     * Creates HTTP request data.
     *
     * @param method HTTP method
     * @param uri    target URI
     * @param body   request body (may be null)
     * @return the protocol data
     */
    public static ProtocolData httpRequest(String method, String uri, Object body) {
        var fields = new LinkedHashMap<String, Object>();
        fields.put("method", method);
        fields.put("uri", uri);
        fields.put("body", body);
        return new ProtocolData("http", "request", fields);
    }

    /**
     * Creates HTTP response data.
     *
     * @param status HTTP status code
     * @param uri    target URI
     * @param body   response body (may be null)
     * @return the protocol data
     */
    public static ProtocolData httpResponse(int status, String uri, Object body) {
        var fields = new LinkedHashMap<String, Object>();
        fields.put("status", status);
        fields.put("uri", uri);
        fields.put("body", body);
        return new ProtocolData("http", "response", fields);
    }

    /**
     * Creates SQL query data.
     *
     * @param sql    the SQL statement
     * @param params the query parameters (may be null)
     * @return the protocol data
     */
    public static ProtocolData sqlQuery(String sql, Map<String, Object> params) {
        var fields = new LinkedHashMap<String, Object>();
        fields.put("sql", sql);
        fields.put("params", params);
        return new ProtocolData("sql", "query", fields);
    }
}
