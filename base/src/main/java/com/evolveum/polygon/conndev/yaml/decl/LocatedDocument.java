/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.yaml.decl;

import com.evolveum.polygon.conndev.concepts.SourceLocation;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.core.TokenStreamLocation;
import tools.jackson.dataformat.yaml.YAMLMapper;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;

/**
 * Parses a YAML document into a location-aware tree of {@link LocatedNode}s.
 *
 * <p>Jackson's {@code readTree()} carries no position info, so this drives the parser itself in a
 * recursive-descent walk, recording each token's line/column. That's what lets {@link
 * DeclYamlBinder} attach a real source location to every value it binds.
 *
 * <p>The raw source text is also retained (see {@link #rawLine}) for detail the tree loses, like a
 * block scalar's indentation.
 */
public final class LocatedDocument {

    private static final YAMLMapper MAPPER = YAMLMapper.builder().build();

    private final String sourceName;
    private final LocatedNode root;
    private final String rawText;
    private String[] rawLines;

    private LocatedDocument(String sourceName, LocatedNode root, String rawText) {
        this.sourceName = sourceName;
        this.root = root;
        this.rawText = rawText;
    }

    public static LocatedDocument parse(String sourceName, String yaml) {
        return parseInternal(sourceName, yaml);
    }

    public static LocatedDocument parse(String sourceName, Reader reader) {
        return parseInternal(sourceName, readFully(sourceName, reader));
    }

    private static LocatedDocument parseInternal(String sourceName, String yaml) {
        try (JsonParser parser = MAPPER.createParser(new StringReader(yaml))) {
            if (parser.nextToken() == null) {
                throw new IllegalArgumentException("Empty YAML document (" + sourceName + ")");
            }
            LocatedNode root = parseValue(parser);
            if (parser.nextToken() != null) {
                throw new IllegalArgumentException("Expected exactly one document per file, found a "
                        + "second document (" + sourceName + ")");
            }
            return new LocatedDocument(sourceName, root, yaml);
        } catch (JacksonException e) {
            throw new IllegalArgumentException("Could not parse YAML (" + sourceName + "): " + e.getMessage(), e);
        }
    }

    private static String readFully(String sourceName, Reader reader) {
        try {
            var out = new StringBuilder();
            var buffer = new char[4096];
            int n;
            while ((n = reader.read(buffer)) != -1) {
                out.append(buffer, 0, n);
            }
            return out.toString();
        } catch (IOException e) {
            throw new IllegalArgumentException("Could not read YAML (" + sourceName + "): " + e.getMessage(), e);
        }
    }

    public String sourceName() {
        return sourceName;
    }

    public LocatedNode root() {
        return root;
    }

    public SourceLocation location(int line, int col) {
        return SourceLocation.from(sourceName, line, col);
    }

    /** The raw source's 1-based line {@code lineNumber}, or {@code null} if out of range. */
    String rawLine(int lineNumber) {
        if (rawLines == null) {
            rawLines = rawText.split("\n", -1);
        }
        int index = lineNumber - 1;
        return index >= 0 && index < rawLines.length ? rawLines[index] : null;
    }

    private static LocatedNode parseValue(JsonParser parser) {
        JsonToken token = parser.currentToken();
        TokenStreamLocation loc = parser.currentTokenLocation();
        int line = loc.getLineNr();
        int col = loc.getColumnNr();

        switch (token) {
            case START_OBJECT: {
                LocatedNode node = LocatedNode.object(line, col);
                while (parser.nextToken() != JsonToken.END_OBJECT) {
                    if (parser.currentToken() != JsonToken.PROPERTY_NAME) {
                        throw new IllegalArgumentException("Expected a property name but found "
                                + parser.currentToken() + " at line " + parser.currentTokenLocation().getLineNr());
                    }
                    String key = parser.getString();
                    TokenStreamLocation keyLoc = parser.currentTokenLocation();
                    parser.nextToken();
                    node.entries().add(new LocatedNode.Entry(key, keyLoc.getLineNr(), keyLoc.getColumnNr(), parseValue(parser)));
                }
                return node;
            }
            case START_ARRAY: {
                LocatedNode node = LocatedNode.array(line, col);
                while (parser.nextToken() != JsonToken.END_ARRAY) {
                    node.elements().add(parseValue(parser));
                }
                return node;
            }
            default:
                return LocatedNode.scalar(line, col, token, parser.getText());
        }
    }
}
