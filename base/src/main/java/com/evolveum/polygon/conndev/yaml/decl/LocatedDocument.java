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

import java.io.Reader;
import java.io.StringReader;

/**
 * Parses a YAML document into a location-aware tree of {@link LocatedNode}s.
 *
 * <p>A Jackson 3 {@code readTree()} result carries no position information (and there is no feature
 * flag to add it), so this drives the {@link JsonParser} itself in a recursive-descent walk and
 * records the 1-based {@link TokenStreamLocation} of every token. That is what lets the
 * {@link DeclYamlBinder} attach a real {@code SourceLocation} (source name + line/column) to every value
 * it binds — something the previous typed-POJO front-end could not do.
 */
public final class LocatedDocument {

    private static final YAMLMapper MAPPER = YAMLMapper.builder().build();

    private final String sourceName;
    private final LocatedNode root;

    private LocatedDocument(String sourceName, LocatedNode root) {
        this.sourceName = sourceName;
        this.root = root;
    }

    public static LocatedDocument parse(String sourceName, String yaml) {
        return parse(sourceName, new StringReader(yaml));
    }

    public static LocatedDocument parse(String sourceName, Reader reader) {
        try (JsonParser parser = MAPPER.createParser(reader)) {
            if (parser.nextToken() == null) {
                throw new IllegalArgumentException("Empty YAML document (" + sourceName + ")");
            }
            LocatedNode root = parseValue(parser);
            if (parser.nextToken() != null) {
                throw new IllegalArgumentException("Expected exactly one document per file, found a "
                        + "second document (" + sourceName + ")");
            }
            return new LocatedDocument(sourceName, root);
        } catch (JacksonException e) {
            throw new IllegalArgumentException("Could not parse YAML (" + sourceName + "): " + e.getMessage(), e);
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
