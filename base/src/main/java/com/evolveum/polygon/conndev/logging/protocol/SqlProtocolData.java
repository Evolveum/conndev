/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.logging.protocol;

import java.util.Map;

/** SQL protocol data (SQL connectors). */
public final class SqlProtocolData {

    private SqlProtocolData() {
    }

    /**
     * An executed SQL query with its parameters.
     *
     * @param sql    the SQL statement
     * @param params the query parameters (may be null)
     */
    public record Query(String sql, Map<String, Object> params) {
    }
}
