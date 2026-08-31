/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.devtools.log;

import java.util.List;

/**
 * An operation entry reconstructed by the {@link EntryCorrelator} from its structured log
 * lines.
 *
 * <p>For standalone message lines (no entry id) the trace contains a single detail event and
 * {@link #id()}, {@link #operation()} and {@link #outcome()} are null.
 *
 * @param id             operation entry id (null for standalone message entries)
 * @param parentId       id of the enclosing entry (for nested entries)
 * @param operation      ConnId operation name
 * @param objectClass    object class the operation applies to
 * @param location       caller location of the first event
 * @param startTs        timestamp of the first event, epoch millis
 * @param endTs          timestamp of the terminating event, epoch millis (null if incomplete)
 * @param severity       highest severity seen across the entry's events
 * @param firstMessage   message of the first event
 * @param outcome        end result (null if the entry is incomplete)
 * @param protocolEvents protocol events in sequence order
 * @param details        detail events in sequence order
 * @param completed      whether the entry saw its terminating result or error event
 * @param childIds       ids of entries nested inside this one
 */
public record OperationTrace(
        String id,
        String parentId,
        String operation,
        String objectClass,
        CallerLocation location,
        long startTs,
        Long endTs,
        LogSeverity severity,
        String firstMessage,
        Outcome outcome,
        List<ProtocolEvent> protocolEvents,
        List<DetailEvent> details,
        boolean completed,
        List<String> childIds) {
}
