/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.devtools.yaml.shape;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * An object-node shape: a closed mapping of YAML keys onto {@link YamlProperty}s. This is the
 * shape of a value bound onto a builder — every property is a binding the binder would accept,
 * and any other key is a runtime failure (which is why the generated artifacts are closed).
 *
 * <p>{@link #sourceType()} is the fully-qualified name of the builder type the shape was scanned
 * from (or the shape-file section for declarative shapes); it drives the generated class naming.
 */
public final class ObjectShape implements YamlShape {

    private final LinkedHashMap<String, YamlProperty> properties;
    private final String sourceType;
    private final String description;
    private final boolean deprecated;
    private final String name;

    public ObjectShape(LinkedHashMap<String, YamlProperty> properties, String sourceType, String description, boolean deprecated) {
        this(properties, sourceType, description, deprecated, null);
    }

    public ObjectShape(LinkedHashMap<String, YamlProperty> properties, String sourceType, String description,
                       boolean deprecated, String name) {
        this.properties = properties;
        this.sourceType = sourceType;
        this.description = description;
        this.deprecated = deprecated;
        this.name = name;
    }

    /** The bound properties, in scan (declaration) order. */
    public Map<String, YamlProperty> properties() {
        return properties;
    }

    /** The builder type this shape was derived from, or {@code ""} for a synthetic shape. */
    public String sourceType() {
        return sourceType;
    }

    /** An explicit generated-class name (the shape file's {@code name}); {@code null} to derive it. */
    public String name() {
        return name;
    }

    @Override
    public String source() {
        return sourceType;
    }

    @Override
    public String description() {
        return description;
    }

    @Override
    public boolean deprecated() {
        return deprecated;
    }

    /** A mutable copy, for merge operations. */
    public static ObjectShape of(String sourceType) {
        return new ObjectShape(new LinkedHashMap<>(), sourceType, null, false);
    }

    /** A copy that carries an explicit generated-class name. */
    public ObjectShape withName(String newName) {
        return new ObjectShape(properties, sourceType, description, deprecated, newName);
    }

    @Override
    public String toString() {
        return "object(" + sourceType + "{" + String.join(", ", properties.keySet()) + "})";
    }
}
