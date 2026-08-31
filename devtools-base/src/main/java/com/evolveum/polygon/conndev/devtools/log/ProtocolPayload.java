/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.devtools.log;

import java.util.Map;

/**
 * Protocol-specific data carried by {@code PROTOCOL} events.
 *
 * <p>The payload is intentionally flat so that new protocol types only need to populate the
 * relevant fields; consumers switch on {@link #type()}.
 *
 * @param type   protocol type, e.g. {@code http} or {@code sql}
 * @param kind   event kind, e.g. {@code request}, {@code response} or {@code query}
 * @param method HTTP method (HTTP protocols)
 * @param uri    target URI (HTTP protocols)
 * @param status HTTP status code (HTTP responses)
 * @param body   request/response body
 * @param sql    SQL statement (SQL protocols)
 * @param params SQL parameters (SQL protocols)
 */
public record ProtocolPayload(
        String type,
        String kind,
        String method,
        String uri,
        Integer status,
        String body,
        String sql,
        Map<String, Object> params) {
}
