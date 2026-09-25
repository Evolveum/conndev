/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.devtools.yaml.shape;

import java.util.List;

/**
 * A scalar leaf. The {@link LeafType} mirrors the coercion of
 * {@code DeclDefaultValueParser} (the runtime leaf coercer): booleans, integers, numbers,
 * strings, enums (case-insensitive constant names) and the raw-text fallback that makes any
 * unrecognised parameter type a string.
 */
public final class LeafShape implements YamlShape {

    private final LeafType type;
    private final List<String> enumValues;
    private final String defaultValue;
    private final String source;
    private final String description;
    private final boolean deprecated;

    public LeafShape(LeafType type, List<String> enumValues, String defaultValue,
                     String source, String description, boolean deprecated) {
        this.type = type;
        this.enumValues = enumValues == null ? List.of() : List.copyOf(enumValues);
        this.defaultValue = defaultValue;
        this.source = source;
        this.description = description;
        this.deprecated = deprecated;
    }

    public LeafType type() {
        return type;
    }

    /** The accepted enum constant names (empty for non-enum leaves). */
    public List<String> enumValues() {
        return enumValues;
    }

    /** A runtime default the binding applies when the key is absent, if known. */
    public String defaultValue() {
        return defaultValue;
    }

    @Override
    public String source() {
        return source;
    }

    @Override
    public String description() {
        return description;
    }

    @Override
    public boolean deprecated() {
        return deprecated;
    }

    public enum LeafType {
        STRING, BOOLEAN, INTEGER, NUMBER
    }

    @Override
    public String toString() {
        return type.name().toLowerCase() + (enumValues.isEmpty() ? "" : "(" + String.join(",", enumValues) + ")");
    }
}
