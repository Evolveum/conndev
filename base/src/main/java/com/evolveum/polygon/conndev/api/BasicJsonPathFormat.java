/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.api;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Parser and serializer for the basic subset of JSONPath that
 * {@link AttributePath} can represent.
 *
 * <p>Supported syntax:</p>
 * <pre>
 * path       = "$" ( "." name | "[" element "]" )*
 * name       = [A-Za-z] [A-Za-z0-9_-]* | quoted-string
 * element    = nonNegativeInteger
 *            | quoted-string
 *            | "?" "(" predicate ")"
 * predicate  = comparison ( ("and" | "&&") comparison )*
 * comparison = "@" ( "." name | "[" quoted-string "]" ) "==" literal
 * literal    = 'string' | "string" | number | true | false | null
 * </pre>
 *
 * <p>Examples: {@code $.name.givenName}, {@code $['name']['givenName']},
 * {@code $.emails[0].value}, {@code $.emails[?(@.type == 'work')].value}.</p>
 *
 * <p>Full JSONPath features that can not be modeled by {@link AttributePath}
 * (recursive descent, wildcards, slices, multi-index selection, filter
 * operators other than {@code ==} combined with {@code and}) are rejected
 * with a {@link ParsingException}.</p>
 */
public final class BasicJsonPathFormat implements AttributePathFormat {

    private static final Pattern UNQUOTED_NAME = Pattern.compile("[A-Za-z][A-Za-z0-9_-]*");

    public static final BasicJsonPathFormat INSTANCE =  new BasicJsonPathFormat();

    private BasicJsonPathFormat() {
        // intentionally empty
    }

    // ==================== Parsing ====================

    /**
     * Parses a basic JSONPath expression into an {@link AttributePath}.
     *
     * @param input the JSONPath expression
     * @return the parsed path (a bare {@code $} yields an empty path)
     * @throws ParsingException if the input is not valid basic JSONPath, or uses
     *         a JSONPath feature that {@link AttributePath} can not represent
     */
    public AttributePath parse(String input) {
        if (input == null) {
            throw new ParsingException("JSONPath must not be null");
        }
        var source = input.strip();
        if (source.isEmpty() || source.charAt(0) != '$') {
            throw new ParsingException("Invalid JSONPath '" + source + "': must start with '$'");
        }
        var parser = new Parser(source);
        parser.pos = 1;
        var components = new ArrayList<AttributePath.Component>();
        while (!parser.isAtEnd()) {
            var c = parser.peek();
            if (c == '.') {
                parser.pos++;
                components.add(parser.readMemberName());
            } else if (c == '[') {
                parseBracket(parser, components);
            } else {
                parser.error("Unexpected character '" + c + "'");
                return null;
            }
        }
        return new AttributePath(List.copyOf(components));
    }

    private static void parseBracket(Parser parser, List<AttributePath.Component> components) {
        parser.expect('[');
        parser.skipSpaces();
        if (parser.isAtEnd()) {
            parser.error("Expected an array index, quoted member, or filter in '[' expression");
            return;
        }
        var c = parser.peek();
        if (c == '?') {
            parseFilter(parser, components);
        } else if (c == '\'' || c == '"') {
            var name = parser.readQuotedString(c);
            parser.skipSpaces();
            parser.expect(']');
            components.add(new AttributePath.Attribute(name));
        } else if (c == '-') {
            parser.error("Negative array index is not supported in JSONPath");
        } else if (Character.isDigit(c)) {
            var start = parser.pos;
            while (!parser.isAtEnd() && Character.isDigit(parser.peek())) {
                parser.pos++;
            }
            var text = parser.input.substring(start, parser.pos);
            int index;
            try {
                index = Integer.parseInt(text);
            } catch (NumberFormatException e) {
                parser.error("Array index out of range: '" + text + "'");
                return;
            }
            parser.skipSpaces();
            parser.expect(']');
            components.add(new AttributePath.IndexFilter(index));
        } else {
            parser.error("Expected an array index, quoted member, or filter in '[' expression");
        }
    }

    private static void parseFilter(Parser parser, List<AttributePath.Component> components) {
        parser.pos++;
        parser.skipSpaces();
        parser.expect('(');
        var values = new LinkedHashMap<String, Object>();
        parseComparison(parser, values);
        while (true) {
            parser.skipSpaces();
            if (!parser.matchesLogicalOperator()) {
                break;
            }
            parseComparison(parser, values);
        }
        parser.skipSpaces();
        parser.expect(')');
        parser.skipSpaces();
        parser.expect(']');
        components.add(new AttributePath.SimpleValueFilter(values));
    }

    private static void parseComparison(Parser parser, Map<String, Object> values) {
        parser.skipSpaces();
        if (!parser.matchesLiteral("@")) {
            parser.error("Expected '@' in filter predicate");
            return;
        }
        parser.skipSpaces();
        if (parser.isAtEnd()) {
            parser.error("Expected a property reference after '@'");
            return;
        }
        var c = parser.peek();
        String key;
        if (c == '.') {
            parser.pos++;
            key = parser.readMemberName().name();
        } else if (c == '[') {
            parser.pos++;
            parser.skipSpaces();
            if (parser.isAtEnd() || (parser.peek() != '\'' && parser.peek() != '"')) {
                parser.error("Expected a quoted property name in '@[...]' predicate");
                return;
            }
            key = parser.readQuotedString(parser.peek());
            parser.skipSpaces();
            parser.expect(']');
        } else {
            parser.error("Expected a property reference after '@'");
            return;
        }
        parser.skipSpaces();
        if (!parser.matchesLiteral("==")) {
            parser.error("Expected '==' in filter predicate (only equality filters are supported)");
            return;
        }
        parser.skipSpaces();
        var value = parser.readLiteral();
        if (values.containsKey(key)) {
            parser.error("Duplicate property '" + key + "' in filter predicate");
            return;
        }
        values.put(key, value);
    }

    // ==================== Serialization ====================

    /**
     * Serializes an {@link AttributePath} to basic JSONPath notation.
     *
     * @param path the path to serialize (an empty path is serialized as {@code $})
     * @return the JSONPath expression
     * @throws AttributePathFormatException if the path contains a component that can not be
     *         represented in basic JSONPath (a negative array index or an unsupported
     *         filter value type)
     */
    public String serialize(AttributePath path) {
        if (path == null) {
            throw new AttributePathFormatException("Cannot serialize a null path to JSONPath");
        }
        var sb = new StringBuilder("$");
        for (var component : path.components()) {
            switch (component) {
                case AttributePath.Attribute attr -> appendMember(sb, attr.name(), false);
                case AttributePath.Extension ext -> appendMember(sb, ext.name(), true);
                case AttributePath.IndexFilter index -> {
                    if (index.index() < 0) {
                        throw new AttributePathFormatException(
                                "Negative array index is not supported in JSONPath: " + index.index());
                    }
                    sb.append('[').append(index.index()).append(']');
                }
                case AttributePath.SimpleValueFilter filter -> {
                    if (filter.keyValues().isEmpty()) {
                        throw new AttributePathFormatException("Cannot serialize an empty value filter to JSONPath");
                    }
                    sb.append("[?(");
                    var first = true;
                    for (var entry : filter.keyValues().entrySet()) {
                        if (!first) {
                            sb.append(" and ");
                        }
                        first = false;
                        appendFilterKey(sb, entry.getKey());
                        sb.append(" == ").append(serializeValue(entry.getValue()));
                    }
                    sb.append(")]");
                }
            }
        }
        return sb.toString();
    }

    private static void appendMember(StringBuilder sb, String name, boolean forceQuoted) {
        if (!forceQuoted && UNQUOTED_NAME.matcher(name).matches()) {
            sb.append('.').append(name);
        } else {
            sb.append("['").append(escapeSingleQuoted(name)).append("']");
        }
    }

    private static void appendFilterKey(StringBuilder sb, String key) {
        if (UNQUOTED_NAME.matcher(key).matches()) {
            sb.append("@.").append(key);
        } else {
            sb.append("@['").append(escapeSingleQuoted(key)).append("']");
        }
    }

    private static String escapeSingleQuoted(String s) {
        var sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            var c = s.charAt(i);
            switch (c) {
                case '\'' -> sb.append("\\'");
                case '\\' -> sb.append("\\\\");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
    }

    private static String serializeValue(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof String s) {
            return "'" + escapeSingleQuoted(s) + "'";
        }
        if (value instanceof Boolean b || value instanceof Number n) {
            return value.toString();
        }
        throw new AttributePathFormatException(
                "Cannot serialize JSONPath filter value of type: " + value.getClass().getName());
    }

    // ==================== Parser ====================

    private static final class Parser {

        private final String input;
        private int pos;

        private Parser(String input) {
            this.input = input;
        }

        private boolean isAtEnd() {
            return pos >= input.length();
        }

        private char peek() {
            return input.charAt(pos);
        }

        private void skipSpaces() {
            while (!isAtEnd() && input.charAt(pos) == ' ') {
                pos++;
            }
        }

        private void expect(char c) {
            if (isAtEnd() || input.charAt(pos) != c) {
                error("Expected '" + c + "'");
            }
            pos++;
        }

        /**
         * Reads a member name: unquoted ([A-Za-z][A-Za-z0-9_-]*) or quoted ('...' or "...").
         */
        private AttributePath.Attribute readMemberName() {
            if (isAtEnd()) {
                error("Expected a member name");
                return null;
            }
            var c = peek();
            if (c == '\'' || c == '"') {
                return new AttributePath.Attribute(readQuotedString(c));
            }
            var start = pos;
            while (!isAtEnd()) {
                var ch = input.charAt(pos);
                if (Character.isLetterOrDigit(ch) || ch == '_' || ch == '-') {
                    pos++;
                } else {
                    break;
                }
            }
            var name = input.substring(start, pos);
            if (!UNQUOTED_NAME.matcher(name).matches()) {
                error("Invalid member name '" + name + "'");
                return null;
            }
            return new AttributePath.Attribute(name);
        }

        private String readQuotedString(char quote) {
            pos++;
            var sb = new StringBuilder();
            while (true) {
                if (isAtEnd()) {
                    error("Unterminated quoted string");
                    return null;
                }
                var c = input.charAt(pos++);
                if (c == quote) {
                    return sb.toString();
                }
                if (c == '\\') {
                    if (isAtEnd()) {
                        error("Unterminated escape in quoted string");
                        return null;
                    }
                    var esc = input.charAt(pos++);
                    switch (esc) {
                        case '\'', '"', '\\' -> sb.append(esc);
                        case 'b' -> sb.append('\b');
                        case 'f' -> sb.append('\f');
                        case 'n' -> sb.append('\n');
                        case 'r' -> sb.append('\r');
                        case 't' -> sb.append('\t');
                        case 'u' -> {
                            if (pos + 4 > input.length()) {
                                error("Invalid unicode escape in quoted string");
                                return null;
                            }
                            try {
                                sb.append((char) Integer.parseInt(input.substring(pos, pos + 4), 16));
                            } catch (NumberFormatException e) {
                                error("Invalid unicode escape in quoted string");
                                return null;
                            }
                            pos += 4;
                        }
                        default -> {
                            error("Invalid escape character '\\" + esc + "' in quoted string");
                            return null;
                        }
                    }
                } else {
                    sb.append(c);
                }
            }
        }

        private boolean matchesLogicalOperator() {
            if (matchesWord("and")) {
                return true;
            }
            return matchesLiteral("&&");
        }

        private boolean matchesWord(String word) {
            if (input.regionMatches(pos, word, 0, word.length())) {
                var next = pos + word.length();
                if (next >= input.length() || !isNameChar(input.charAt(next))) {
                    pos = next;
                    return true;
                }
            }
            return false;
        }

        private boolean matchesLiteral(String text) {
            if (input.startsWith(text, pos)) {
                pos += text.length();
                return true;
            }
            return false;
        }

        private Object readLiteral() {
            if (isAtEnd()) {
                error("Expected a value in filter predicate");
                return null;
            }
            var c = peek();
            if (c == '\'' || c == '"') {
                return readQuotedString(c);
            }
            if (matchesWord("true")) {
                return Boolean.TRUE;
            }
            if (matchesWord("false")) {
                return Boolean.FALSE;
            }
            if (matchesWord("null")) {
                return null;
            }
            var start = pos;
            while (!isAtEnd()) {
                var ch = input.charAt(pos);
                if (Character.isDigit(ch) || ch == '-' || ch == '.' || ch == 'e' || ch == 'E') {
                    pos++;
                } else {
                    break;
                }
            }
            var text = input.substring(start, pos);
            if (text.isEmpty()) {
                error("Expected a value in filter predicate, got '" + c + "'");
                return null;
            }
            return readNumber(text);
        }

        private Number readNumber(String text) {
            if (!text.contains(".") && !text.contains("e") && !text.contains("E")) {
                try {
                    return Integer.parseInt(text);
                } catch (NumberFormatException ignored) {
                    try {
                        return Long.parseLong(text);
                    } catch (NumberFormatException ignored2) {
                        // fall through to double
                    }
                }
            }
            try {
                return Double.parseDouble(text);
            } catch (NumberFormatException e) {
                error("Invalid number in filter predicate: '" + text + "'");
                return null;
            }
        }

        private ParsingException error(String message) {
            throw new ParsingException("Invalid JSONPath '" + input + "': " + message
                    + " (position " + pos + ")");
        }

        private static boolean isNameChar(char c) {
            return Character.isLetterOrDigit(c) || c == '_' || c == '-' || c == '$';
        }
    }
}
