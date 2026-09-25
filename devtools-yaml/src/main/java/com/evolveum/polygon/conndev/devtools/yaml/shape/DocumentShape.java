/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.devtools.yaml.shape;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The generated document: the top-level YAML keys of one connector framework's declarative
 * document (the union of the schema, operations and — where the front-end has one —
 * authentication envelopes). Built by {@code SchemaGeneration} from the shape file's entries;
 * keys bound by several slots (the {@code objectClasses} union) are merged into one property.
 */
public final class DocumentShape {

    private final String name;
    private final LinkedHashMap<String, YamlProperty> topLevel;
    private final List<String> requiredTopLevel;
    private final String generatedBy;

    public DocumentShape(String name, LinkedHashMap<String, YamlProperty> topLevel,
                         List<String> requiredTopLevel, String generatedBy) {
        this.name = name;
        this.topLevel = topLevel;
        this.requiredTopLevel = List.copyOf(requiredTopLevel);
        this.generatedBy = generatedBy;
    }

    /** The document name (e.g. {@code scimrest}) — used in titles and output file names. */
    public String name() {
        return name;
    }

    /** The top-level properties, in shape-file order. */
    public Map<String, YamlProperty> topLevel() {
        return topLevel;
    }

    /** The top-level keys the runtime loaders require to be present. */
    public List<String> requiredTopLevel() {
        return requiredTopLevel;
    }

    /** The tool/version line for the generated artifacts' headers. */
    public String generatedBy() {
        return generatedBy;
    }

    @Override
    public String toString() {
        return "document(" + name + "{" + String.join(", ", topLevel.keySet()) + "})";
    }
}
