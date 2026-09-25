/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.devtools.yaml.shape;

/**
 * An attribute-path value ({@code @Yaml.Path}): either a scalar path expression (in the binding's
 * default format) or a mapping {@code {type: <format name>, value: <expression>}} with a required
 * {@code value}. The expression is not validated as a path at binding time — the declaration
 * parses it lazily — so the shape only constrains the structure.
 */
public final class PathShape implements YamlShape {

    private final String source;
    private final String description;
    private final boolean deprecated;

    public PathShape(String source, String description, boolean deprecated) {
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
        return "path";
    }
}
