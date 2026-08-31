/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.devtools.log;

import java.util.Map;

/**
 * A single structured log event, i.e. the JSON payload of one structured log line.
 *
 * <p>Only the fields relevant to the event type are populated.
 *
 * @param v           format version
 * @param id          operation entry id (null for standalone message events)
 * @param parentId    id of the enclosing entry (for nested entries)
 * @param seq         sequence number of the event within the entry
 * @param ts          event timestamp, epoch millis
 * @param severity    event severity
 * @param thread      name of the thread that emitted the event
 * @param event       event type
 * @param operation   ConnId operation name (e.g. {@code search}, {@code create})
 * @param objectClass object class the operation applies to
 * @param message     human-readable message
 * @param location    caller location of the code that emitted the event
 * @param detail      key/value detail data (detail events)
 * @param protocol    protocol data (protocol events)
 * @param result      end result data (result events)
 * @param error       failure data (error events)
 */
public record StructuredLogEvent(
        Integer v,
        String id,
        String parentId,
        Integer seq,
        Long ts,
        LogSeverity severity,
        String thread,
        EventType event,
        String operation,
        String objectClass,
        String message,
        CallerLocation location,
        Map<String, Object> detail,
        ProtocolPayload protocol,
        ResultPayload result,
        ErrorPayload error) {
}
