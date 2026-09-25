/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.devtools.yaml.shape;

/**
 * A sequence (YAML list) shape: every item must match {@link #item()}. Used for the list shapes
 * the {@code @Yaml.Custom} handlers accept (endpoints, supported filters, …).
 */
public final class ListShape implements YamlShape {

    private final YamlShape item;
    private final String source;
    private final String description;
    private final boolean deprecated;

    public ListShape(YamlShape item, String source, String description, boolean deprecated) {
        this.item = item;
        this.source = source;
        this.description = description;
        this.deprecated = deprecated;
    }

    public YamlShape item() {
        return item;
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
        return "list[" + item + "]";
    }
}
