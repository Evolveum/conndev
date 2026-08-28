/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.api;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Parser and serializer for JSON Pointer (RFC 6901).
 *
 * <p>Examples: {@code /name/givenName}, {@code /emails/0/value},
 * {@code /a~1b} (a member named {@code a/b}), {@code /a~0b} (a member named
 * {@code a~b}). The empty string denotes the root document and parses to an
 * empty {@link AttributePath}.</p>
 *
 * <p>JSON Pointer has no concept of value filters, so serializing a path that
 * contains an {@link AttributePath.SimpleValueFilter} fails with
 * {@link AttributePathFormatException}.</p>
 */
public final class JsonPointerFormat implements AttributePathFormat {

    /**
     * An array index reference token: {@code 0} or a non-zero digit without leading zeros
     * (per RFC 6901, a token such as {@code 01} denotes an object member name, not an index).
     */
    private static final Pattern ARRAY_INDEX = Pattern.compile("0|[1-9][0-9]*");
    public static final JsonPointerFormat INSTANCE = new JsonPointerFormat();

    private JsonPointerFormat() {
    }

    // ==================== Parsing ====================

    /**
     * Parses a JSON Pointer (RFC 6901) into an {@link AttributePath}.
     *
     * @param input the JSON Pointer string (the empty string denotes the root document)
     * @return the parsed path
     * @throws ParsingException if the input is not a valid absolute JSON Pointer
     */
    public AttributePath parse(String input) {
        if (input == null) {
            throw new ParsingException("JSON Pointer must not be null");
        }
        if (input.isEmpty()) {
            return new AttributePath(List.of());
        }
        if (input.charAt(0) != '/') {
            throw new ParsingException("Invalid JSON Pointer '" + input + "': absolute pointers must start with '/'");
        }
        var tokens = input.substring(1).split("/", -1);
        var components = new ArrayList<AttributePath.Component>();
        for (var token : tokens) {
            var name = unescape(token);
            if (ARRAY_INDEX.matcher(name).matches()) {
                int index;
                try {
                    index = Integer.parseInt(name);
                } catch (NumberFormatException e) {
                    throw new ParsingException(
                            "Invalid JSON Pointer '" + input + "': array index out of range: '" + name + "'");
                }
                components.add(new AttributePath.IndexFilter(index));
            } else {
                components.add(new AttributePath.Attribute(name));
            }
        }
        return new AttributePath(List.copyOf(components));
    }

    private static String unescape(String token) {
        var sb = new StringBuilder(token.length());
        for (int i = 0; i < token.length(); i++) {
            var c = token.charAt(i);
            if (c == '~' && i + 1 < token.length()) {
                var next = token.charAt(i + 1);
                if (next == '0') {
                    sb.append('~');
                    i++;
                    continue;
                }
                if (next == '1') {
                    sb.append('/');
                    i++;
                    continue;
                }
            }
            sb.append(c);
        }
        return sb.toString();
    }

    // ==================== Serialization ====================

    /**
     * Serializes an {@link AttributePath} to a JSON Pointer (RFC 6901).
     *
     * @param path the path to serialize (an empty path is serialized as the empty string)
     * @return the JSON Pointer string
     * @throws AttributePathFormatException if the path contains a component that can not be
     *         represented in a JSON Pointer (a value filter or a negative array index)
     */
    public String serialize(AttributePath path) {
        if (path == null) {
            throw new AttributePathFormatException("Cannot serialize a null path to a JSON Pointer");
        }
        var sb = new StringBuilder();
        for (var component : path.components()) {
            switch (component) {
                case AttributePath.Attribute attr -> sb.append('/').append(escape(attr.name()));
                case AttributePath.Extension ext -> sb.append('/').append(escape(ext.name()));
                case AttributePath.IndexFilter index -> {
                    if (index.index() < 0) {
                        throw new AttributePathFormatException(
                                "Negative array index is not representable in a JSON Pointer: " + index.index());
                    }
                    sb.append('/').append(index.index());
                }
                case AttributePath.SimpleValueFilter filter ->
                        throw new AttributePathFormatException(
                                "Value filters are not representable in a JSON Pointer (RFC 6901)");
            }
        }
        return sb.toString();
    }

    private static String escape(String token) {
        return token.replace("~", "~0").replace("/", "~1");
    }
}
