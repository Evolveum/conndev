/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.api;

import com.evolveum.polygon.conndev.concepts.SourceLocation;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Canonical, by-name access to the built-in {@link AttributePathFormat} implementations.
 *
 * <p>The names are the ones the declarative YAML {@code type} entry of a
 * {@code @Yaml.Path} binding accepts (e.g. {@code path: { type: JSON_POINTER, value: /a/b }})
 * and mirror the Groovy DSL {@code PathBuilder} constants ({@code JSON_PATH}, {@code JSON_POINTER},
 * {@code SCIM}).</p>
 */
public final class StringAttributePathFormats {

    /** {@link BasicJsonPathFormat} — the basic JSONPath subset ({@code $.a[0]}, value filters). */
    public static final String JSON_PATH = "JSON_PATH";

    /** {@link JsonPointerFormat} — JSON Pointer (RFC 6901, {@code /a/0/b}). */
    public static final String JSON_POINTER = "JSON_POINTER";

    /** {@link ScimPath} — SCIM attribute path (RFC 7643/7644, {@code name.givenName}). */
    public static final String SCIM = "SCIM";

    private static final Map<String, AttributePathFormat<String>> BY_NAME = new LinkedHashMap<>();

    static {
        register(JSON_PATH, BasicJsonPathFormat.INSTANCE);
        register(JSON_POINTER, JsonPointerFormat.INSTANCE);
    }

    private StringAttributePathFormats() {
    }

    public static void register(String name, AttributePathFormat<String> format) {
        BY_NAME.putIfAbsent(name, format);
    }

    /**
     * Resolves a format name (case-insensitive, trimmed) to its built-in format instance.
     *
     * @param name the format name as written by the user
     * @param location the source location for error reporting (may be {@code null})
     * @return the format instance
     * @throws IllegalArgumentException if the name is unknown; the message lists the supported names
     */
    public static AttributePathFormat<String> byName(String name, SourceLocation location) {
        Objects.requireNonNull(name, "path format name must not be null");
        for (var entry : BY_NAME.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(name.trim())) {
                return entry.getValue();
            }
        }
        throw new IllegalArgumentException("Unknown path format '" + name + "'" + suffix(location)
                + " (supported: " + String.join(", ", BY_NAME.keySet()) + ")");
    }

    /** The supported format names, in declaration order. */
    public static List<String> names() {
        return List.copyOf(BY_NAME.keySet());
    }

    private static String suffix(SourceLocation location) {
        return location != null ? " at " + location : "";
    }
}
