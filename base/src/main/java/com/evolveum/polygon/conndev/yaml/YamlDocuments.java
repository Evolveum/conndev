/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.yaml;

import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.dataformat.yaml.YAMLMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.util.List;

/**
 * Shared YAML plumbing of the conndev configuration core: a single fail-fast {@link ObjectMapper}
 * (unknown keys reject) and the "exactly one document per file" convention that every YAML
 * front-end relies on. Both the built-in {@link YamlSchemaLoader} and the protocol-specific
 * operation/authentication loaders of the connectors read their typed documents through here, so
 * the Jackson configuration and the one-document rule live in one place.
 *
 * <p>The {@code documentLabel} names what a single document represents (e.g. {@code "object class"}),
 * so the one-document-per-file error stays meaningful for each front-end.
 */
public final class YamlDocuments {

    private static final ObjectMapper YAML = YAMLMapper.builder()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true)
            .build();

    private YamlDocuments() {
    }

    /**
     * Converts an already-parsed node (e.g. a protocol-specific block captured by
     * {@link YamlProtocolBlockConsumer}) into a typed value, using the same fail-fast
     * (unknown-key-rejecting) mapper as the rest of the YAML schema DSL.
     */
    public static <T> T convert(JsonNode node, Class<T> type) {
        try {
            return YAML.treeToValue(node, type);
        } catch (JacksonException e) {
            throw new IllegalArgumentException("Could not parse protocol block: " + e.getMessage(), e);
        }
    }

    /** Reads exactly one document of {@code type} from an inline YAML string. */
    public static <T> T readSingle(String yaml, Class<T> type, String documentLabel, String sourceName) {
        return read(() -> YAML.createParser(yaml), type, documentLabel, sourceName);
    }

    /** Reads exactly one document of {@code type} from a reader. */
    public static <T> T readSingle(Reader reader, Class<T> type, String documentLabel, String sourceName) {
        return read(() -> YAML.createParser(reader), type, documentLabel, sourceName);
    }

    /**
     * Reads exactly one document of {@code type} from a classpath resource, resolved relative to
     * {@code anchor} (mirroring {@code anchor.getResourceAsStream(resource)}).
     */
    public static <T> T readSingleFromResource(Class<?> anchor, String resource, Class<T> type, String documentLabel) {
        InputStream stream = anchor.getResourceAsStream(resource);
        if (stream == null) {
            throw new IllegalArgumentException("YAML resource not found: " + resource);
        }
        return read(() -> YAML.createParser(stream), type, documentLabel, resource);
    }

    private static <T> T read(ParserSource source, Class<T> type, String documentLabel, String sourceName) {
        try (JsonParser parser = source.create()) {
            List<T> documents = YAML.readValues(parser, type).readAll();
            if (documents.size() != 1) {
                throw new IllegalArgumentException("Expected exactly one " + documentLabel + " per file, found "
                        + documents.size() + " documents (" + sourceName + ")");
            }
            return documents.get(0);
        } catch (IOException | JacksonException e) {
            throw new IllegalArgumentException("Could not parse YAML (" + sourceName + "): " + e.getMessage(), e);
        }
    }

    @FunctionalInterface
    private interface ParserSource {
        JsonParser create() throws IOException;
    }
}
