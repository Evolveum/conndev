/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.yaml.decl;

import com.evolveum.polygon.conndev.concepts.SourceLocation;

import java.math.BigDecimal;
import java.math.BigInteger;

/**
 * The built-in {@link DeclYamlValueParser} covering the primitive wrappers, numeric types, character and enums.
 * Anything it does not recognise falls back to the raw text, so a {@code String}-typed parameter
 * always works and semantic types (ConnId value types, {@code AttributePath}, filters, …) are
 * handled by dedicated coercers supplied via {@code @Yaml.ValueParser}.
 */
final class DeclDefaultValueParser implements DeclYamlValueParser {

    public static final DeclYamlValueParser INSTANCE = new DeclDefaultValueParser();

    @Override
    public Object coerce(LocatedNode value, SourceLocation location, Class<?> targetType) {
        String text = value.text();
        if (targetType == String.class) {
            return text;
        }
        if (targetType == Boolean.class || targetType == boolean.class) {
            return value.asBoolean();
        }
        if (targetType == Integer.class || targetType == int.class) {
            return value.asInt();
        }
        if (targetType == Long.class || targetType == long.class) {
            return value.asLong();
        }
        if (targetType == Double.class || targetType == double.class) {
            return value.asDouble();
        }
        if (targetType == Float.class || targetType == float.class) {
            return (float) value.asDouble();
        }
        if (targetType == Short.class || targetType == short.class) {
            return (short) value.asInt();
        }
        if (targetType == Byte.class || targetType == byte.class) {
            return (byte) value.asInt();
        }
        if (targetType == Character.class || targetType == char.class) {
            return text.charAt(0);
        }
        if (targetType == BigInteger.class) {
            return value.asBigInteger();
        }
        if (targetType == BigDecimal.class) {
            return value.asBigDecimal();
        }
        if (targetType.isEnum()) {
            return coerceEnum(value, location, (Class<? extends Enum<?>>) targetType);
        }
        // Fallback: hand the raw text through (String-typed params and anything else).
        return text;
    }

    @SuppressWarnings("unchecked")
    private static <E extends Enum<E>> E coerceEnum(LocatedNode value, SourceLocation location,
            Class<? extends Enum<?>> type) {
        String text = value.text() == null ? "" : value.text().trim();
        for (Enum<?> constant : type.getEnumConstants()) {
            if (constant.name().equalsIgnoreCase(text)) {
                return (E) constant;
            }
        }
        throw new IllegalArgumentException("Unknown " + type.getSimpleName() + " '" + text + "' at " + location);
    }
}
