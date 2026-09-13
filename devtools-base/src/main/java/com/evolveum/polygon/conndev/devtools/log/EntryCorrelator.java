/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.devtools.log;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Stateful correlator that reconstructs operation entries from structured log lines.
 *
 * <p>Events are grouped by entry id and ordered by their sequence number, so lines arriving
 * out of order are still assembled correctly. An entry is considered complete once a
 * {@code RESULT} or {@code ERROR} event is seen. Standalone message events (no entry id) are
 * kept separately and each becomes a single-event trace.
 *
 * <p>Instances are not thread-safe; use one correlator per consumer thread.
 */
public final class EntryCorrelator {

    private final Map<String, List<StructuredLogEvent>> eventsById = new LinkedHashMap<>();
    private final List<StructuredLogEvent> messages = new ArrayList<>();

    /**
     * Parses the line if it carries a structured log payload and feeds it into the correlator.
     *
     * @param line a raw log line
     */
    public void acceptLine(String line) {
        var event = OperationLogParser.parseLine(line);
        if (event != null) {
            accept(event);
        }
    }

    /**
     * Accepts an already-parsed structured log event.
     *
     * @param event the parsed event
     */
    public void accept(StructuredLogEvent event) {
        if (event == null) {
            return;
        }
        if (event.id() == null) {
            messages.add(event);
            return;
        }
        eventsById.computeIfAbsent(event.id(), k -> new ArrayList<>()).add(event);
    }

    /**
     * Checks whether the given entry has seen its terminating {@code RESULT} or {@code ERROR}
     * event.
     *
     * @param id the entry id
     * @return true if the entry is complete
     */
    public boolean isComplete(String id) {
        var events = eventsById.get(id);
        if (events == null) {
            return false;
        }
        return events.stream().anyMatch(EntryCorrelator::isTerminating);
    }

    /**
     * Finalizes and removes all entries that have completed, in entry order. Incomplete
     * entries and standalone messages remain pending.
     *
     * @return the completed entries
     */
    public Collection<OperationTrace> drainComplete() {
        var completedIds = eventsById.keySet().stream().filter(this::isComplete)
                .collect(Collectors.toCollection(HashSet::new));
        var pending = new LinkedHashMap<String, List<StructuredLogEvent>>();
        var traces = new ArrayList<OperationTrace>();
        eventsById.forEach((id, events) -> {
            if (completedIds.contains(id)) {
                traces.add(finalizeEntry(id, events));
            } else {
                pending.put(id, events);
            }
        });
        eventsById.clear();
        eventsById.putAll(pending);
        return linkChildren(traces);
    }

    /**
     * Finalizes and removes <em>all</em> pending entries (complete or not) and standalone
     * messages, in entry order.
     *
     * @return all pending entries
     */
    public Collection<OperationTrace> drain() {
        var traces = new ArrayList<OperationTrace>();
        eventsById.forEach((id, events) -> traces.add(finalizeEntry(id, events)));
        eventsById.clear();
        messages.forEach(event -> traces.add(finalizeMessage(event)));
        messages.clear();
        return linkChildren(traces);
    }

    // ========================================================================
    // Internals
    // ========================================================================

    private static boolean isTerminating(StructuredLogEvent event) {
        return event.event() == EventType.RESULT || event.event() == EventType.ERROR;
    }

    private static long ts(StructuredLogEvent event) {
        return event.ts() == null ? 0L : event.ts();
    }

    private static OperationTrace finalizeEntry(String id, List<StructuredLogEvent> raw) {
        var events = new ArrayList<StructuredLogEvent>(raw);
        events.sort(Comparator.comparingInt(e -> e.seq() == null ? 0 : e.seq()));

        var first = events.getFirst();
        var startTs = ts(first);

        LogSeverity severity = null;
        var parentId = first.parentId();
        var operation = first.operation();
        var objectClass = first.objectClass();
        var location = first.location();
        var protocolEvents = new ArrayList<ProtocolEvent>();
        var details = new ArrayList<DetailEvent>();
        Outcome outcome = null;
        Long endTs = null;

        for (var event : events) {
            if (event.severity() != null && (severity == null || event.severity().rank() > severity.rank())) {
                severity = event.severity();
            }
            if (operation == null && event.operation() != null) {
                operation = event.operation();
            }
            if (objectClass == null && event.objectClass() != null) {
                objectClass = event.objectClass();
            }
            if (parentId == null && event.parentId() != null) {
                parentId = event.parentId();
            }
            if (location == null && event.location() != null) {
                location = event.location();
            }
            if (event.protocol() != null) {
                protocolEvents.add(new ProtocolEvent(ts(event), event.severity(), event.location(), event.protocol(), event.message()));
            }
            if (event.detail() != null || event.event() == EventType.MESSAGE) {
                details.add(new DetailEvent(ts(event), event.severity(), event.location(), event.detail(), event.message()));
            }
            if (event.event() == EventType.RESULT && event.result() != null) {
                outcome = new Outcome(event.result().ok(), event.result().value(), event.message(), null);
                endTs = ts(event);
            } else if (event.event() == EventType.ERROR && event.error() != null) {
                outcome = Outcome.failure(event.error().message(), event.error().stacktrace());
                endTs = ts(event);
            }
        }

        return new OperationTrace(id, parentId, operation, objectClass, location, startTs, endTs, severity,
                first.message(), outcome, List.copyOf(protocolEvents), List.copyOf(details),
                events.stream().anyMatch(EntryCorrelator::isTerminating), null);
    }

    private static OperationTrace finalizeMessage(StructuredLogEvent event) {
        var ts = ts(event);
        Map<String, Object> detail;
        if (event.detail() != null) {
            detail = event.detail();
        } else if (event.message() != null) {
            detail = Map.of(ConndevLogFormat.DETAIL_MESSAGE_KEY, event.message());
        } else {
            detail = Map.of();
        }
        var details = List.of(new DetailEvent(ts, event.severity(), event.location(), detail, event.message()));
        return new OperationTrace(null, null, null, null, event.location(), ts, null,
                event.severity(), event.message(), null, List.of(), details, true, List.of());
    }

    private static List<OperationTrace> linkChildren(List<OperationTrace> traces) {
        var result = new ArrayList<OperationTrace>(traces.size());
        for (var trace : traces) {
            var childIds = traces.stream()
                    .filter(t -> t.id() != null && t.parentId() != null && t.parentId().equals(trace.id()))
                    .map(OperationTrace::id)
                    .toList();
            result.add(new OperationTrace(trace.id(), trace.parentId(), trace.operation(), trace.objectClass(),
                    trace.location(), trace.startTs(), trace.endTs(), trace.severity(), trace.firstMessage(),
                    trace.outcome(), trace.protocolEvents(), trace.details(), trace.completed(),
                    childIds.isEmpty() ? List.of() : List.copyOf(childIds)));
        }
        return result;
    }
}
