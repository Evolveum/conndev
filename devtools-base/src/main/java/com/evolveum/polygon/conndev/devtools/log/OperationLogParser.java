/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.devtools.log;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Parser for conndev structured log lines.
 *
 * <p>Recognizes lines carrying the {@link ConndevLogFormat#MARKER}, deserializes the JSON
 * payload and correlates the events into {@link OperationTrace} entries. Lines without the
 * marker (regular log output) are ignored.
 */
public final class OperationLogParser {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private OperationLogParser() {
    }

    /**
     * Checks whether the line carries a structured log payload.
     *
     * @param line a raw log line
     * @return true if the line is a structured log line
     */
    public static boolean isStructuredLine(String line) {
        var index = ConndevLogFormat.markerIndex(line);
        if (index < 0) {
            return false;
        }
        return payloadOf(line, index).startsWith("{");
    }

    /**
     * Parses a single structured log line.
     *
     * @param line a raw log line
     * @return the parsed event, or null if the line is not a valid structured log line
     */
    public static StructuredLogEvent parseLine(String line) {
        var index = ConndevLogFormat.markerIndex(line);
        if (index < 0) {
            return null;
        }
        var payload = payloadOf(line, index);
        if (!payload.startsWith("{")) {
            return null;
        }
        try {
            return MAPPER.readValue(payload, StructuredLogEvent.class);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    /**
     * Parses a stream of raw log lines and returns the reconstructed operation entries.
     *
     * @param lines the raw log lines
     * @return the reconstructed entries, in entry order
     */
    public static List<OperationTrace> parse(Collection<String> lines) {
        var correlator = new EntryCorrelator();
        for (String line : lines) {
            correlator.acceptLine(line);
        }
        return List.copyOf(correlator.drain());
    }

    /**
     * Reads all lines from the given reader and returns the reconstructed operation entries.
     *
     * @param reader the reader of a log stream or file
     * @return the reconstructed entries, in entry order
     * @throws IOException if reading fails
     */
    public static List<OperationTrace> parse(Reader reader) throws IOException {
        var lines = new ArrayList<String>();
        try (var buffered = new BufferedReader(reader)) {
            String line;
            while ((line = buffered.readLine()) != null) {
                lines.add(line);
            }
        }
        return parse(lines);
    }

    private static String payloadOf(String line, int index) {
        return line.substring(index + ConndevLogFormat.MARKER.length()).trim();
    }
}
