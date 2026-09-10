/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.devtools.log;

import java.util.List;

/**
 * Constants defining the conndev structured log line format (the wire contract between the
 * logging facade and its consumers).
 *
 * <p>A structured log line consists of an optional human-readable message followed by the
 * {@link #MARKER} and a single-line JSON payload:
 *
 * <pre>{@code
 * REST failed conndev-log/v1 {"v":1,"id":"…","seq":2,"severity":"ERROR","event":"ERROR",…}
 * }</pre>
 *
 * <p>The JSON payload is self-contained (timestamp, severity, thread and caller location are
 * carried inside it), so consumers do not need to parse the standard log line prefix.
 */
public final class ConndevLogFormat {

    /** Marker separating the human-readable message from the JSON payload. */
    public static final String MARKER = "conndev-log/v1";

    /** Format version. */
    public static final int VERSION = 1;

    /** Protocol type for HTTP-based protocols (SCIM/REST). */
    public static final String PROTOCOL_HTTP = "http";

    /** Protocol type for SQL-based protocols. */
    public static final String PROTOCOL_SQL = "sql";

    /** Protocol kind for an outgoing HTTP request. */
    public static final String HTTP_REQUEST = "request";

    /** Protocol kind for a received HTTP response. */
    public static final String HTTP_RESPONSE = "response";

    /** Protocol kind for an executed SQL query. */
    public static final String SQL_QUERY = "query";

    /** Protocol kind for the body of an outgoing HTTP request (companion of {@link #HTTP_REQUEST}). */
    public static final String HTTP_REQUEST_BODY = "request-body";

    /** Protocol kind for the body of a received HTTP response (companion of {@link #HTTP_RESPONSE}). */
    public static final String HTTP_RESPONSE_BODY = "response-body";

    /** Default {@code detail} key used for free-form detail messages. */
    public static final String DETAIL_MESSAGE_KEY = "message";

    /** Default maximum length of protocol bodies, in characters. */
    public static final int DEFAULT_MAX_BODY_LENGTH = 64 * 1024;

    /** Field names considered sensitive by default (their values are redacted). */
    public static final List<String> DEFAULT_SENSITIVE_NAMES = List.of(
            "authorization", "password", "token", "secret", "credential",
            "api_key", "apikey", "private_key");

    private ConndevLogFormat() {
    }

    /**
     * Returns the index of the format marker within the line.
     *
     * @return the marker index, or -1 if the line carries no structured payload
     */
    public static int markerIndex(String line) {
        return line == null ? -1 : line.indexOf(MARKER);
    }

    /**
     * Formats a structured log line from the human-readable message and the JSON payload.
     *
     * @param message the human-readable message (may be null or empty)
     * @param json    the single-line JSON payload
     * @return the formatted line
     */
    public static String formatLine(String message, String json) {
        var sb = new StringBuilder();
        if (message != null && !message.isEmpty()) {
            sb.append(message).append(' ');
        }
        sb.append(MARKER).append(' ').append(json);
        return sb.toString();
    }
}
