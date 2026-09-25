/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.devtools.yaml.scan;

import com.evolveum.polygon.conndev.devtools.yaml.shape.LeafShape;
import com.evolveum.polygon.conndev.devtools.yaml.shape.LeafShape.LeafType;
import com.evolveum.polygon.conndev.devtools.yaml.shape.YamlShape;

import java.util.ArrayList;
import java.util.List;

/**
 * Maps a leaf binding's parameter type to its {@link LeafShape}, mirroring the runtime coercer
 * ({@code DeclDefaultValueParser}): booleans, integers, numbers, strings, enums (constant names,
 * matched case-insensitively at runtime) and the raw-text fallback — anything the runtime
 * coerces to raw text is a string in the schema.
 *
 * <p>A {@code @Yaml.ValueParser} with a declared shape in the shape file overrides the
 * auto-inferred leaf (the coercer there is semantic — e.g. a ConnId type name list).
 */
final class TypeMapper {

    private TypeMapper() {
    }

    static YamlShape leaf(Class<?> paramType, String parserFqn,
                          YamlShape parserDeclaredShape, String source, String description, boolean deprecated, GapReport report) {
        if (parserDeclaredShape != null) {
            // The shape file is authoritative for a declared coercer; keep its leaf (re-stamp source only).
            if (parserDeclaredShape instanceof LeafShape leaf) {
                return new LeafShape(leaf.type(), leaf.enumValues(), leaf.defaultValue(), source, leaf.description(), deprecated);
            }
            return parserDeclaredShape;
        }
        LeafType type;
        List<String> enumValues = List.of();
        if (paramType == boolean.class || paramType == Boolean.class) {
            type = LeafType.BOOLEAN;
        } else if (paramType == int.class || paramType == Integer.class
                || paramType == long.class || paramType == Long.class
                || paramType == short.class || paramType == Short.class
                || paramType == byte.class || paramType == Byte.class
                || paramType == java.math.BigInteger.class) {
            type = LeafType.INTEGER;
        } else if (paramType == double.class || paramType == Double.class
                || paramType == float.class || paramType == Float.class
                || paramType == java.math.BigDecimal.class) {
            type = LeafType.NUMBER;
        } else if (paramType == String.class || paramType == char.class || paramType == Character.class
                || CharSequence.class.isAssignableFrom(paramType)) {
            type = LeafType.STRING;
        } else if (paramType.isEnum()) {
            type = LeafType.STRING;
            List<String> constants = new ArrayList<>();
            for (Object constant : paramType.getEnumConstants()) {
                constants.add(((Enum<?>) constant).name());
            }
            enumValues = List.copyOf(constants);
        } else {
            type = LeafType.STRING;
            if (parserFqn == null) {
                report.warn("Leaf type " + paramType.getName() + " at " + source + " is not a known scalar; "
                        + "treating it as a string (the runtime coercer falls back to raw text). "
                        + "Declare a shape in the shape file's valueParsers/entries for a tighter schema.");
            }
        }
        return new LeafShape(type, enumValues, null, source, description, deprecated);
    }
}
