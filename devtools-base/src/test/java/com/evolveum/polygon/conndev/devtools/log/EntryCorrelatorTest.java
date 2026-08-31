/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.devtools.log;

import org.testng.annotations.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link EntryCorrelator}: streaming acceptance, completion tracking, ordering and
 * nested entry linkage.
 */
public class EntryCorrelatorTest {

    private static String line(String id, int seq, String event, String extra) {
        var payload = "{\"v\":1,\"id\":\"" + id + "\",\"seq\":" + seq + ",\"ts\":" + (1761741185000L + seq)
                + ",\"severity\":\"INFO\",\"thread\":\"main\",\"event\":\"" + event.toUpperCase() + "\""
                + ",\"operation\":\"search\",\"objectClass\":\"User\",\"message\":\"seq " + seq + "\""
                + (extra == null ? "" : "," + extra) + "}";
        return "prefix " + ConndevLogFormat.formatLine("msg-" + seq, payload);
    }

    // ========================================================================
    // Streaming
    // ========================================================================

    @Test
    public void completionIsTracked() {
        var correlator = new EntryCorrelator();
        correlator.acceptLine(line("op-1", 0, "operation", null));
        correlator.acceptLine(line("op-1", 1, "protocol",
                "\"protocol\":{\"type\":\"http\",\"kind\":\"request\",\"method\":\"GET\",\"uri\":\"/users\"}"));

        assertThat(correlator.isComplete("op-1")).isFalse();
        assertThat(correlator.drainComplete()).isEmpty();

        correlator.acceptLine(line("op-1", 2, "result", "\"result\":{\"ok\":true,\"value\":\"uid-1\"}"));
        assertThat(correlator.isComplete("op-1")).isTrue();

        var drained = correlator.drainComplete();
        assertThat(drained).hasSize(1);
        var trace = drained.iterator().next();
        assertThat(trace.id()).isEqualTo("op-1");
        assertThat(trace.completed()).isTrue();
        assertThat(trace.outcome().ok()).isTrue();
        assertThat(trace.outcome().value()).isEqualTo("uid-1");
        assertThat(correlator.drain()).isEmpty();
    }

    @Test
    public void eventsAreOrderedBySequenceRegardlessOfArrivalOrder() {
        var correlator = new EntryCorrelator();
        correlator.acceptLine(line("op-1", 2, "protocol",
                "\"protocol\":{\"type\":\"http\",\"kind\":\"response\",\"status\":200,\"uri\":\"/users\"}"));
        correlator.acceptLine(line("op-1", 0, "operation", null));
        correlator.acceptLine(line("op-1", 1, "protocol",
                "\"protocol\":{\"type\":\"http\",\"kind\":\"request\",\"method\":\"GET\",\"uri\":\"/users\"}"));
        correlator.acceptLine(line("op-1", 3, "result", "\"result\":{\"ok\":true,\"value\":null}"));

        var trace = correlator.drain().iterator().next();
        assertThat(trace.protocolEvents()).hasSize(2);
        assertThat(trace.protocolEvents().get(0).protocol().kind()).isEqualTo(ConndevLogFormat.HTTP_REQUEST);
        assertThat(trace.protocolEvents().get(1).protocol().kind()).isEqualTo(ConndevLogFormat.HTTP_RESPONSE);
        assertThat(trace.endTs()).isEqualTo(1761741185003L);
    }

    @Test
    public void incompleteEntriesAreFlaggedOnDrain() {
        var correlator = new EntryCorrelator();
        correlator.acceptLine(line("op-1", 0, "operation", null));
        correlator.acceptLine(line("op-1", 1, "protocol",
                "\"protocol\":{\"type\":\"sql\",\"kind\":\"query\",\"sql\":\"SELECT 1\"}"));

        var trace = correlator.drain().iterator().next();
        assertThat(trace.completed()).isFalse();
        assertThat(trace.outcome()).isNull();
        assertThat(trace.endTs()).isNull();
        assertThat(trace.protocolEvents()).hasSize(1);
        assertThat(trace.startTs()).isEqualTo(1761741185000L);
    }

    // ========================================================================
    // Standalone messages
    // ========================================================================

    @Test
    public void standaloneMessagesBecomeEntriesOnDrain() {
        var correlator = new EntryCorrelator();
        var messageLine = "prefix msg conndev-log/v1 "
                + "{\"v\":1,\"ts\":1761741185000,\"severity\":\"INFO\",\"thread\":\"main\",\"event\":\"MESSAGE\","
                + "\"message\":\"Connection established\"}";
        correlator.acceptLine(messageLine);
        correlator.acceptLine(line("op-1", 0, "operation", null));

        // the standalone message does not prevent draining of complete entries
        correlator.acceptLine(line("op-1", 1, "result", "\"result\":{\"ok\":true,\"value\":\"x\"}"));
        var complete = correlator.drainComplete();
        assertThat(complete).hasSize(1);
        assertThat(complete.iterator().next().id()).isEqualTo("op-1");

        var rest = correlator.drain();
        assertThat(rest).hasSize(1);
        assertThat(rest.iterator().next().id()).isNull();
        assertThat(rest.iterator().next().firstMessage()).isEqualTo("Connection established");
    }

    // ========================================================================
    // Nested entries
    // ========================================================================

    @Test
    public void nestedEntriesAreLinkedToTheirParent() {
        var correlator = new EntryCorrelator();
        correlator.acceptLine(line("op-parent", 0, "operation", null));
        var childStart = "prefix conndev-log/v1 "
                + "{\"v\":1,\"id\":\"op-child\",\"parentId\":\"op-parent\",\"seq\":0,\"ts\":1761741185001,"
                + "\"severity\":\"DEBUG\",\"thread\":\"main\",\"event\":\"OPERATION\",\"operation\":\"lookup\","
                + "\"objectClass\":\"User\",\"message\":\"aux lookup\"}";
        correlator.acceptLine(childStart);
        correlator.acceptLine(line("op-child", 1, "result", "\"result\":{\"ok\":true,\"value\":\"1\"}"));
        correlator.acceptLine(line("op-parent", 1, "result", "\"result\":{\"ok\":true,\"value\":\"2\"}"));

        var traces = correlator.drain().stream().toList();
        var parent = traces.stream().filter(t -> "op-parent".equals(t.id())).findFirst().orElseThrow();
        var child = traces.stream().filter(t -> "op-child".equals(t.id())).findFirst().orElseThrow();

        assertThat(child.parentId()).isEqualTo("op-parent");
        assertThat(parent.childIds()).containsExactly("op-child");
        assertThat(child.completed()).isTrue();
        assertThat(parent.completed()).isTrue();
    }

    // ========================================================================
    // Foreign lines
    // ========================================================================

    @Test
    public void foreignLinesAreIgnored() {
        var correlator = new EntryCorrelator();
        correlator.acceptLine("2026-10-24 14:32:05.000 INFO [main] c.e.Foo plain line");
        correlator.acceptLine(line("op-1", 0, "operation", null));
        correlator.acceptLine("2026-10-24 14:32:05.001 WARN [main] c.e.Foo another plain line");
        correlator.acceptLine(line("op-1", 1, "result", "\"result\":{\"ok\":true,\"value\":\"y\"}"));

        var traces = correlator.drain();
        assertThat(traces).hasSize(1);
        assertThat(traces.iterator().next().id()).isEqualTo("op-1");
    }

    @Test
    public void multipleEntriesAreKeptInArrivalOrder() {
        var correlator = new EntryCorrelator();
        correlator.acceptLine(line("op-a", 0, "operation", null));
        correlator.acceptLine(line("op-b", 0, "operation", null));
        correlator.acceptLine(line("op-a", 1, "result", "\"result\":{\"ok\":true,\"value\":\"a\"}"));
        correlator.acceptLine(line("op-b", 1, "result", "\"result\":{\"ok\":true,\"value\":\"b\"}"));

        var traces = correlator.drain();
        assertThat(traces).extracting(OperationTrace::id).containsExactly("op-a", "op-b");
        assertThat(traces).allSatisfy(t -> assertThat(t.completed()).isTrue());
    }
}
