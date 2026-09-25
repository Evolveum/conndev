/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.devtools.yaml.shape;

import java.util.List;

/**
 * A mapping (YAML map) shape: every entry's value must match {@link #value()}. This is the shape
 * of a {@code @Yaml.Map} block ({@code attributes:}, {@code references:}, the top-level
 * {@code objectClasses:} block, …). When {@link #restrictedKeys()} is non-empty, only those keys
 * are accepted (the class-level {@code connId:} alias map, e.g. {@code UID}/{@code NAME}).
 */
public final class MapShape implements YamlShape {

    private final YamlShape value;
    private final List<String> restrictedKeys;
    private final String source;
    private final String description;
    private final boolean deprecated;
    private final boolean nullableValue;

    public MapShape(YamlShape value, List<String> restrictedKeys, String source, String description, boolean deprecated) {
        this(value, restrictedKeys, source, description, deprecated, false);
    }

    public MapShape(YamlShape value, List<String> restrictedKeys, String source, String description,
                    boolean deprecated, boolean nullableValue) {
        this.value = value;
        this.restrictedKeys = List.copyOf(restrictedKeys);
        this.source = source;
        this.description = description;
        this.deprecated = deprecated;
        this.nullableValue = nullableValue;
    }

    public YamlShape value() {
        return value;
    }

    /** The accepted entry keys, or empty for an unbounded name map. */
    public List<String> restrictedKeys() {
        return restrictedKeys;
    }

    /** Whether an entry's value may be an explicit {@code null} (e.g. {@code attributes: {foo: null}}). */
    public boolean nullableValue() {
        return nullableValue;
    }

    /** A copy with the entry-value nullability flipped to {@code true} (the shapes are immutable). */
    public MapShape withNullableValue() {
        return new MapShape(value, restrictedKeys, source, description, deprecated, true);
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

    @Override
    public String toString() {
        return "map{" + (restrictedKeys.isEmpty() ? "string" : String.join("|", restrictedKeys)) + " -> "
                + value + (nullableValue ? " | null" : "") + "}";
    }
}
