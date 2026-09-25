/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.devtools.yaml.shape;

import java.util.List;

/** A value that must match at least one of the alternatives (JSONSchema {@code oneOf}). */
public final class UnionShape implements YamlShape {

    private final List<YamlShape> alternatives;
    private final String source;
    private final String description;
    private final boolean deprecated;

    public UnionShape(List<YamlShape> alternatives, String source, String description, boolean deprecated) {
        if (alternatives.size() < 2) {
            throw new IllegalArgumentException("A union needs at least two alternatives");
        }
        this.alternatives = List.copyOf(alternatives);
        this.source = source;
        this.description = description;
        this.deprecated = deprecated;
    }

    public List<YamlShape> alternatives() {
        return alternatives;
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
        return "oneOf(" + String.join(" | ", alternatives.stream().map(Object::toString).toList()) + ")";
    }
}
