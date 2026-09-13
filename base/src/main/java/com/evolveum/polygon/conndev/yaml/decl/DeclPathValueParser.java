/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.yaml.decl;

import com.evolveum.polygon.conndev.api.AttributePathDeclaration;
import com.evolveum.polygon.conndev.api.AttributePathFormat;
import com.evolveum.polygon.conndev.api.StringAttributePathFormats;
import com.evolveum.polygon.conndev.concepts.DefinitionValue;
import com.evolveum.polygon.conndev.concepts.SourceLocation;

/**
 * The {@code @Yaml.Path} coercer: converts the YAML value of an attribute-path binding into an
 * {@link AttributePathDeclaration} in the binding's format, carrying the key's
 * {@link SourceLocation}.
 *
 * <p>Two shapes are accepted:</p>
 * <pre>
 * path: name.givenName                    # a scalar — the binding's default format
 * path:                                   # a mapping — explicit format
 *   type: JSON_POINTER                    # optional; defaults to the binding's format
 *   value: /name/givenName
 * </pre>
 *
 * <p>The expression is <em>not</em> parsed here: the declaration parses it lazily on first
 * {@link AttributePathDeclaration#actual()} access, so an invalid expression surfaces when the
 * mapping is built (the builders force the parse in their {@code build()}), with the declared
 * source location attached to the error. Structural problems (wrong shape, unknown keys,
 * unknown {@code type} name) fail fast here, at binding time.</p>
 */
public final class DeclPathValueParser implements DeclYamlValueParser {

    private final AttributePathFormat<String> defaultFormat;

    public DeclPathValueParser(AttributePathFormat<String> defaultFormat) {
        this.defaultFormat = defaultFormat;
    }

    @Override
    public Object coerce(LocatedNode value, SourceLocation location, Class<?> targetType) {
        AttributePathFormat<String> format;
        String expression;
        if (value.kind() == LocatedNode.Kind.SCALAR) {
            format = defaultFormat;
            expression = requireExpression(value, location);
        } else if (value.kind() == LocatedNode.Kind.OBJECT) {
            for (var entry : value.entries()) {
                if (!entry.key().equals("type") && !entry.key().equals("value")) {
                    throw fail("unknown key '" + entry.key() + "' (supported: type, value)", location);
                }
            }
            LocatedNode valueNode = value.get("value");
            if (valueNode == null) {
                throw fail("a mapping path requires a 'value'", location);
            }
            format = resolveFormat(value, location);
            expression = requireExpression(valueNode, location);
        } else {
            throw fail("expected a path expression string (or a {type, value} mapping), got a list", location);
        }
        return AttributePathDeclaration.of(
                DefinitionValue.from(format, location),
                DefinitionValue.from(expression, location));
    }

    private AttributePathFormat<String> resolveFormat(LocatedNode mapping, SourceLocation location) {
        LocatedNode type = mapping.get("type");
        if (type == null) {
            return defaultFormat;
        }
        if (type.kind() != LocatedNode.Kind.SCALAR || !type.isValue()) {
            throw fail("'type' must be a string naming a known format", location);
        }
        return StringAttributePathFormats.byName(type.text(), location);
    }

    private static String requireExpression(LocatedNode node, SourceLocation location) {
        if (node.kind() != LocatedNode.Kind.SCALAR) {
            throw fail("the path expression must be a string", location);
        }
        String text = node.text();
        if (text == null || text.isBlank()) {
            throw fail("the path expression must not be blank", location);
        }
        return text;
    }

    private static IllegalArgumentException fail(String detail, SourceLocation location) {
        return new IllegalArgumentException("Invalid path declaration: " + detail + " at " + location);
    }
}
