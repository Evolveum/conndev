/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.devtools.yaml.shape;

/** An unconstrained value: any YAML node is accepted (a gap fallback, declared deliberately). */
public final class AnyShape implements YamlShape {

    private final String source;
    private final String description;
    private final boolean deprecated;

    public AnyShape(String source, String description, boolean deprecated) {
        this.source = source;
        this.description = description;
        this.deprecated = deprecated;
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
        return "any";
    }
}
