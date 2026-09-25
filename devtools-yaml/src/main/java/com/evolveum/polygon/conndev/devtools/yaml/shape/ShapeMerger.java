/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.devtools.yaml.shape;

import java.util.ArrayList;
import java.util.LinkedHashMap;

/**
 * Merges two shapes that must describe the same YAML position — most importantly the two slots
 * (schema + operations) that both bind onto the same {@code objectClasses.<name>} entry, and the
 * delegate-type layers of a {@link DelegatedObjectShape}.
 *
 * <p>Merging is structural: objects merge property-wise (a key present in both must merge
 * recursively, a key present in one is taken over), leaf enums are unioned, and anything else
 * must be structurally identical (per {@link ShapeIdentity}) or the merge fails with a
 * {@link ShapeMergeConflictException} naming the conflicting position.
 */
public final class ShapeMerger {

    /** A merge failure: the two shapes cannot describe the same YAML value. */
    public static final class ShapeMergeConflictException extends RuntimeException {

        public ShapeMergeConflictException(String message) {
            super(message);
        }
    }

    private ShapeMerger() {
    }

    /** Merges {@code into} and {@code other}; either may be mutated (callers pass owned instances). */
    public static YamlShape merge(YamlShape into, YamlShape other, String position) {
        if (into instanceof ObjectShape a && other instanceof ObjectShape b) {
            for (var entry : b.properties().entrySet()) {
                String key = entry.getKey();
                YamlProperty existing = a.properties().get(key);
                if (existing == null) {
                    a.properties().put(key, entry.getValue());
                } else {
                    YamlShape merged = merge(existing.shape(), entry.getValue().shape(), position + "." + key);
                    YamlProperty updated = existing.toBuilder()
                            .shape(merged)
                            .required(existing.required() || entry.getValue().required())
                            .nullable(existing.nullable() || entry.getValue().nullable())
                            .build();
                    a.properties().put(key, updated);
                }
            }
            // The merged object keeps {@code a}'s sourceType (naming prefers the first slot).
            return a;
        }
        if (into instanceof ListShape l && other instanceof ListShape o) {
            YamlShape item = merge(l.item(), o.item(), position + "[]");
            return new ListShape(item, l.source(), firstNonEmpty(l.description(), o.description()), l.deprecated() || o.deprecated());
        }
        if (into instanceof MapShape m && other instanceof MapShape o) {
            if (!m.restrictedKeys().equals(o.restrictedKeys())) {
                throw conflict(position, "map key sets differ: " + m.restrictedKeys() + " vs " + o.restrictedKeys());
            }
            YamlShape value = merge(m.value(), o.value(), position + ".*");
            return new MapShape(value, m.restrictedKeys(), m.source(), firstNonEmpty(m.description(), o.description()),
                    m.deprecated() || o.deprecated(), m.nullableValue() || o.nullableValue());
        }
        if (into instanceof LeafShape a && other instanceof LeafShape b) {
            if (a.type() != b.type()) {
                throw conflict(position, "leaf types differ: " + a.type() + " vs " + b.type());
            }
            ArrayList<String> enumValues = new ArrayList<>(a.enumValues());
            for (String v : b.enumValues()) {
                if (!enumValues.contains(v)) {
                    enumValues.add(v);
                }
            }
            return new LeafShape(a.type(), enumValues, firstNonEmpty(a.defaultValue(), b.defaultValue()),
                    a.source(), firstNonEmpty(a.description(), b.description()), a.deprecated() || b.deprecated());
        }
        if (into instanceof ScriptShape s && other instanceof ScriptShape o) {
            if (s.expression() != o.expression()) {
                throw conflict(position, "script kinds differ: expression vs block");
            }
            return new ScriptShape(s.expression(), s.emptyBody() || o.emptyBody(), s.source(),
                    firstNonEmpty(s.description(), o.description()), s.deprecated() || o.deprecated());
        }
        if (ShapeIdentity.of(into).equals(ShapeIdentity.of(other))) {
            return into;
        }
        throw conflict(position, "shapes differ: " + into + " vs " + other);
    }

    /** Merges the properties of the {@code delegates} (in order) into {@code declared}. */
    public static ObjectShape mergeDelegates(ObjectShape declared, LinkedHashMap<String, YamlProperty> delegates, String position) {
        for (var entry : delegates.entrySet()) {
            String key = entry.getKey();
            YamlProperty existing = declared.properties().get(key);
            if (existing == null) {
                declared.properties().put(key, entry.getValue());
            } else {
                YamlShape merged = merge(existing.shape(), entry.getValue().shape(), position + "." + key);
                declared.properties().put(key, existing.toBuilder()
                        .shape(merged)
                        .required(existing.required() || entry.getValue().required())
                        .nullable(existing.nullable() || entry.getValue().nullable())
                        .build());
            }
        }
        return declared;
    }

    private static String firstNonEmpty(String a, String b) {
        return (a == null || a.isEmpty()) ? b : a;
    }

    private static ShapeMergeConflictException conflict(String position, String detail) {
        return new ShapeMergeConflictException("Shape merge conflict at '" + position + "': " + detail);
    }
}
