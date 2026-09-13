/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.devtools.log;

import org.testng.annotations.Test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link OperationLogParser} using the synthetic {@code sample-operations.log}
 * fixture.
 */
public class OperationLogParserTest {

    private static final List<String> FIXTURE_LINES = readFixtureLines();

    private static final List<OperationTrace> FIXTURE = OperationLogParser.parse(FIXTURE_LINES);

    private static List<String> readFixtureLines() {
        var stream = OperationLogParserTest.class.getResourceAsStream("/sample-operations.log");
        assertThat(stream).isNotNull();
        try (var reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            var lines = new ArrayList<String>();
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.isBlank()) {
                    lines.add(line);
                }
            }
            return lines;
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    // ========================================================================
    // parseLine
    // ========================================================================

    @Test
    public void parsesStructuredLine() {
        var line = "2026-10-24 14:32:08.310 ERROR [main] c.e.Foo REST failed conndev-log/v1 "
                + "{\"v\":1,\"id\":\"op-1\",\"seq\":3,\"ts\":1761741188315,\"severity\":\"ERROR\",\"thread\":\"main\","
                + "\"event\":\"ERROR\",\"operation\":\"search\",\"objectClass\":\"User\",\"message\":\"REST failed\","
                + "\"location\":{\"className\":\"com.example.Foo\",\"methodName\":\"fetch\",\"file\":\"Foo.java\",\"line\":47},"
                + "\"error\":{\"message\":\"REST failed\",\"stacktrace\":[\"boom\"]}}";

        assertThat(OperationLogParser.isStructuredLine(line)).isTrue();

        var event = OperationLogParser.parseLine(line);
        assertThat(event).isNotNull();
        assertThat(event.v()).isEqualTo(ConndevLogFormat.VERSION);
        assertThat(event.id()).isEqualTo("op-1");
        assertThat(event.seq()).isEqualTo(3);
        assertThat(event.ts()).isEqualTo(1761741188315L);
        assertThat(event.severity()).isEqualTo(LogSeverity.ERROR);
        assertThat(event.event()).isEqualTo(EventType.ERROR);
        assertThat(event.operation()).isEqualTo("search");
        assertThat(event.objectClass()).isEqualTo("User");
        assertThat(event.message()).isEqualTo("REST failed");
        assertThat(event.error().message()).isEqualTo("REST failed");
        assertThat(event.error().stacktrace()).containsExactly("boom");
    }

    @Test
    public void rejectsNonStructuredLines() {
        assertThat(OperationLogParser.parseLine("regular log line without payload")).isNull();
        assertThat(OperationLogParser.parseLine(null)).isNull();
        assertThat(OperationLogParser.parseLine("broken conndev-log/v1 not-json-at-all")).isNull();
        assertThat(OperationLogParser.parseLine("broken conndev-log/v1 {\"id\":")).isNull();
        assertThat(OperationLogParser.isStructuredLine("regular log line without payload")).isFalse();
    }

    @Test
    public void parsesTraceSeverityProtocolBodyLine() {
        var line = "HTTP request body /scim/v2/Users conndev-log/v1 "
                + "{\"v\":1,\"id\":\"op-1\",\"seq\":4,\"ts\":1761741188400,\"severity\":\"TRACE\",\"thread\":\"main\","
                + "\"event\":\"PROTOCOL\",\"operation\":\"search\",\"objectClass\":\"User\","
                + "\"message\":\"HTTP request body /scim/v2/Users\","
                + "\"protocol\":{\"type\":\"http\",\"kind\":\"request-body\",\"method\":\"GET\",\"uri\":\"/scim/v2/Users\","
                + "\"body\":\"{\\\"userName\\\":\\\"alice\\\"}\"}}";

        var event = OperationLogParser.parseLine(line);
        assertThat(event).isNotNull();
        assertThat(event.severity()).isEqualTo(LogSeverity.TRACE);
        assertThat(event.event()).isEqualTo(EventType.PROTOCOL);
        assertThat(event.protocol().kind()).isEqualTo(ConndevLogFormat.HTTP_REQUEST_BODY);
        assertThat(event.protocol().body()).contains("alice");
    }

    @Test
    public void traceSeverityRanksBelowDebug() {
        assertThat(LogSeverity.TRACE.atLeast(LogSeverity.DEBUG)).isFalse();
        assertThat(LogSeverity.DEBUG.atLeast(LogSeverity.TRACE)).isTrue();
        assertThat(LogSeverity.fromName("trace")).isEqualTo(LogSeverity.TRACE);
    }

    @Test
    public void toleratesUnknownFields() {
        var line = "msg conndev-log/v1 {\"v\":1,\"id\":\"op-x\",\"futureField\":{\"nested\":true},\"severity\":\"INFO\",\"event\":\"MESSAGE\"}";
        var event = OperationLogParser.parseLine(line);
        assertThat(event).isNotNull();
        assertThat(event.id()).isEqualTo("op-x");
    }

    // ========================================================================
    // parse (fixture)
    // ========================================================================

    @Test
    public void reconstructsEntriesFromFixture() {
        assertThat(FIXTURE).hasSize(6);

        var scim = byId(FIXTURE, "op-scim");
        assertThat(scim).isNotNull();
        assertThat(scim.operation()).isEqualTo("search");
        assertThat(scim.objectClass()).isEqualTo("User");
        assertThat(scim.completed()).isTrue();
        assertThat(scim.severity()).isEqualTo(LogSeverity.ERROR);
        assertThat(scim.firstMessage()).isEqualTo("Executing SCIM request");
        assertThat(scim.startTs()).isEqualTo(1761741185100L);
        assertThat(scim.endTs()).isEqualTo(1761741188315L);
        assertThat(scim.location()).isEqualTo("User.search.groovy");

        assertThat(scim.protocolEvents()).hasSize(2);
        var request = scim.protocolEvents().getFirst();
        assertThat(request.protocol().type()).isEqualTo(ConndevLogFormat.PROTOCOL_HTTP);
        assertThat(request.protocol().kind()).isEqualTo(ConndevLogFormat.HTTP_REQUEST);
        assertThat(request.protocol().method()).isEqualTo("GET");
        assertThat(request.protocol().uri()).isEqualTo("/scim/v2/Users?startIndex=51&count=50");
        assertThat(request.location()).isEqualTo("fetch(RestPagingAwareObjectRetriever.java:47)");
        var response = scim.protocolEvents().get(1);
        assertThat(response.protocol().kind()).isEqualTo(ConndevLogFormat.HTTP_RESPONSE);
        assertThat(response.protocol().status()).isEqualTo(504);
        assertThat(response.protocol().body()).contains("Gateway Timeout");

        assertThat(scim.details()).isEmpty();
        assertThat(scim.outcome().ok()).isFalse();
        assertThat(scim.outcome().message()).isEqualTo("REST failed");
        assertThat(scim.outcome().stacktrace()).hasSize(3);
    }

    @Test
    public void reconstructsSqlEntryFromFixture() {
        var sql = byId(FIXTURE, "op-sql");
        assertThat(sql).isNotNull();
        assertThat(sql.completed()).isTrue();
        assertThat(sql.outcome().ok()).isTrue();
        assertThat(sql.outcome().value()).isEqualTo("1 row");
        assertThat(sql.severity()).isEqualTo(LogSeverity.INFO);
        assertThat(sql.endTs()).isEqualTo(1761741189130L);

        assertThat(sql.protocolEvents()).hasSize(1);
        var query = sql.protocolEvents().getFirst();
        assertThat(query.protocol().type()).isEqualTo(ConndevLogFormat.PROTOCOL_SQL);
        assertThat(query.protocol().sql()).isEqualTo("SELECT * FROM accounts WHERE external_id = ?");
        assertThat(query.protocol().params()).containsEntry("external_id", "u-42");

        assertThat(sql.details()).hasSize(1);
        assertThat(sql.details().getFirst().detail()).containsEntry("auxiliaryAccountLookup", true);
    }

    @Test
    public void standaloneMessagesBecomeSingleEventEntries() {
        var messages = FIXTURE.stream().filter(t -> t.id() == null).toList();
        assertThat(messages).hasSize(4);
        assertThat(messages.getFirst().firstMessage()).isEqualTo("Connecting to REST endpoint");
        assertThat(messages.getFirst().severity()).isEqualTo(LogSeverity.INFO);
        assertThat(messages.getFirst().details()).hasSize(1);
        assertThat(messages.getFirst().details().getFirst().detail())
                .containsEntry(ConndevLogFormat.DETAIL_MESSAGE_KEY, "Connecting to REST endpoint");
        assertThat(messages.get(2).firstMessage()).isEqualTo("Attribute 'manager' has no mapping defined");
        assertThat(messages.get(2).severity()).isEqualTo(LogSeverity.WARN);
        assertThat(messages.get(3).firstMessage()).isEqualTo("Validation stopped");
        assertThat(messages).allSatisfy(t -> {
            assertThat(t.completed()).isTrue();
            assertThat(t.outcome()).isNull();
            assertThat(t.operation()).isNull();
        });
    }

    @Test
    public void parsesFromReader() throws Exception {
        var traces = OperationLogParser.parse(new StringReader(String.join("\n", FIXTURE_LINES)));
        assertThat(traces).hasSameSizeAs(FIXTURE);
        assertThat(traces).extracting(OperationTrace::id)
                .containsExactly("op-scim", "op-sql", null, null, null, null);
    }

    @Test
    public void plainLinesAreIgnored() {
        var plainLines = readFixtureLines().stream()
                .filter(line -> !OperationLogParser.isStructuredLine(line))
                .toList();
        assertThat(plainLines).hasSize(2);
        // fixture has 12 structured + 2 plain lines; entries = 2 operations + 4 messages
        assertThat(FIXTURE).hasSize(6);
    }

    private static OperationTrace byId(List<OperationTrace> traces, String id) {
        return traces.stream().filter(t -> id.equals(t.id())).findFirst().orElse(null);
    }
}
