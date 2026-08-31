/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.logging.protocol;

/** HTTP protocol data (SCIM/REST connectors). */
public final class HttpProtocolData {

    private HttpProtocolData() {
    }

    /**
     * An outgoing HTTP request.
     *
     * @param method HTTP method (e.g. {@code GET})
     * @param uri    target URI including query string
     * @param body   request body (may be null)
     */
    public record Request(String method, String uri, Object body) {
    }

    /**
     * A received HTTP response.
     *
     * @param status HTTP status code
     * @param uri    target URI
     * @param body   response body (may be null)
     */
    public record Response(int status, String uri, Object body) {
    }
}
