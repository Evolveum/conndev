/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.yaml.decl;

import tools.jackson.core.JsonToken;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeFactory;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A single node of the location-aware YAML tree produced by {@link LocatedDocument}.
 *
 * <p>Every node records the 1-based line/column of its own token; object nodes additionally record
 * the location of each property name (key) so the binder can attach a precise
 * {@code SourceLocation} (the key's position) to the values it binds.
 */
public final class LocatedNode {

    public enum Kind { OBJECT, ARRAY, SCALAR }

    private final Kind kind;
    private final int line;
    private final int col;
    private final List<Entry> entries;          // OBJECT only
    private final List<LocatedNode> elements;   // ARRAY only
    private final JsonToken scalarToken;        // SCALAR only
    private final String scalarText;            // SCALAR only (raw text via getText())

    private LocatedNode(Kind kind, int line, int col, List<Entry> entries, List<LocatedNode> elements,
            JsonToken scalarToken, String scalarText) {
        this.kind = kind;
        this.line = line;
        this.col = col;
        this.entries = entries;
        this.elements = elements;
        this.scalarToken = scalarToken;
        this.scalarText = scalarText;
    }

    static LocatedNode object(int line, int col) {
        return new LocatedNode(Kind.OBJECT, line, col, new ArrayList<>(), null, null, null);
    }

    static LocatedNode array(int line, int col) {
        return new LocatedNode(Kind.ARRAY, line, col, null, new ArrayList<>(), null, null);
    }

    static LocatedNode scalar(int line, int col, JsonToken token, String text) {
        return new LocatedNode(Kind.SCALAR, line, col, null, null, token, text);
    }

    public Kind kind() {
        return kind;
    }

    public int line() {
        return line;
    }

    public int col() {
        return col;
    }

    public boolean isNull() {
        return kind == Kind.SCALAR && scalarToken == JsonToken.VALUE_NULL;
    }

    public boolean isValue() {
        return kind == Kind.SCALAR && !isNull();
    }

    public String text() {
        return kind == Kind.SCALAR ? scalarText : null;
    }

    public boolean asBoolean() {
        if (scalarToken == JsonToken.VALUE_TRUE) {
            return true;
        }
        if (scalarToken == JsonToken.VALUE_FALSE) {
            return false;
        }
        if (scalarToken == JsonToken.VALUE_STRING) {
            return Boolean.parseBoolean(scalarText);
        }
        throw new IllegalStateException("Not a boolean value: " + scalarText);
    }

    public int asInt() {
        if (scalarToken == JsonToken.VALUE_NUMBER_INT) {
            return Integer.parseInt(scalarText);
        }
        if (scalarToken == JsonToken.VALUE_STRING) {
            return Integer.parseInt(scalarText.trim());
        }
        throw new IllegalStateException("Not an integer value: " + scalarText);
    }

    public long asLong() {
        if (scalarToken == JsonToken.VALUE_NUMBER_INT) {
            return Long.parseLong(scalarText);
        }
        if (scalarToken == JsonToken.VALUE_STRING) {
            return Long.parseLong(scalarText.trim());
        }
        throw new IllegalStateException("Not a long value: " + scalarText);
    }

    public double asDouble() {
        if (scalarToken == JsonToken.VALUE_NUMBER_FLOAT || scalarToken == JsonToken.VALUE_NUMBER_INT) {
            return Double.parseDouble(scalarText);
        }
        if (scalarToken == JsonToken.VALUE_STRING) {
            return Double.parseDouble(scalarText.trim());
        }
        throw new IllegalStateException("Not a number value: " + scalarText);
    }

    public BigInteger asBigInteger() {
        return new BigInteger(scalarText.trim());
    }

    public BigDecimal asBigDecimal() {
        return new BigDecimal(scalarText.trim());
    }

    /** The ordered key/value entries of an object node (empty for non-objects). */
    public List<Entry> entries() {
        return entries != null ? entries : Collections.emptyList();
    }

    /** The elements of an array node (empty for non-arrays). */
    public List<LocatedNode> elements() {
        return elements != null ? elements : Collections.emptyList();
    }

    /** The value of the named key of an object node, or {@code null} if absent. */
    public LocatedNode get(String key) {
        for (Entry entry : entries()) {
            if (entry.key().equals(key)) {
                return entry.value();
            }
        }
        return null;
    }

    /**
     * Converts this node back into a plain Jackson {@link JsonNode} (dropping the location
     * information). Used to hand a protocol-specific block (e.g. {@code sql:}, {@code scim:}) to a
     * {@code YamlProtocolBlockConsumer}, which deserializes it with the shared fail-fast mapper.
     */
    public JsonNode toJacksonNode() {
        var factory = JsonNodeFactory.instance;
        switch (kind) {
            case OBJECT: {
                var node = factory.objectNode();
                for (Entry entry : entries) {
                    node.set(entry.key, entry.value.toJacksonNode());
                }
                return node;
            }
            case ARRAY: {
                var node = factory.arrayNode();
                for (LocatedNode element : elements) {
                    node.add(element.toJacksonNode());
                }
                return node;
            }
            default:
                return scalarToJacksonNode(factory);
        }
    }

    private JsonNode scalarToJacksonNode(JsonNodeFactory factory) {
        if (scalarToken == JsonToken.VALUE_NULL) {
            return factory.nullNode();
        }
        if (scalarToken == JsonToken.VALUE_TRUE || scalarToken == JsonToken.VALUE_FALSE) {
            return factory.booleanNode(scalarToken == JsonToken.VALUE_TRUE);
        }
        if (scalarToken == JsonToken.VALUE_NUMBER_INT) {
            BigInteger bigInteger = new BigInteger(scalarText);
            if (bigInteger.bitLength() <= 31) {
                return factory.numberNode(bigInteger.intValue());
            }
            if (bigInteger.bitLength() <= 63) {
                return factory.numberNode(bigInteger.longValue());
            }
            return factory.numberNode(bigInteger);
        }
        if (scalarToken == JsonToken.VALUE_NUMBER_FLOAT) {
            return factory.numberNode(new BigDecimal(scalarText));
        }
        return factory.stringNode(scalarText);
    }

    /**
     * A key/value pair of an object node, carrying the property name and its own 1-based location.
     */
    public static final class Entry {
        private final String key;
        private final int keyLine;
        private final int keyCol;
        private final LocatedNode value;

        Entry(String key, int keyLine, int keyCol, LocatedNode value) {
            this.key = key;
            this.keyLine = keyLine;
            this.keyCol = keyCol;
            this.value = value;
        }

        public String key() {
            return key;
        }

        public int keyLine() {
            return keyLine;
        }

        public int keyCol() {
            return keyCol;
        }

        public LocatedNode value() {
            return value;
        }
    }
}
