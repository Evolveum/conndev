/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.devtools.yaml.codegen;

import com.evolveum.polygon.conndev.devtools.yaml.config.ShapeFileConfig;
import com.evolveum.polygon.conndev.devtools.yaml.shape.DocumentShape;
import com.evolveum.polygon.conndev.devtools.yaml.shape.ListShape;
import com.evolveum.polygon.conndev.devtools.yaml.shape.MapShape;
import com.evolveum.polygon.conndev.devtools.yaml.shape.ObjectShape;
import com.evolveum.polygon.conndev.devtools.yaml.shape.ShapeIdentity;
import com.evolveum.polygon.conndev.devtools.yaml.shape.UnionShape;
import com.evolveum.polygon.conndev.devtools.yaml.shape.YamlProperty;
import com.evolveum.polygon.conndev.devtools.yaml.shape.YamlShape;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Assigns the generated names (JSONSchema {@code $defs} / Pydantic class names) to the document's
 * distinct object structures, shared by both generators so the two artifacts name the same
 * structure identically. The name is the shape-file override, else the builder type's simple name
 * with the {@code Impl}/{@code Builder} suffixes stripped; the {@code objectClasses} entry's value
 * is always {@code ObjectClass} (the union of its slots), and name collisions between distinct
 * structures get numeric suffixes.
 */
public final class ShapeNaming {

    /** The union object of the {@code objectClasses} entries. */
    public static final String OBJECT_CLASS_NAME = "ObjectClass";

    private final ShapeFileConfig config;
    private final LinkedHashMap<String, ObjectShape> shapes = new LinkedHashMap<>();
    private final Map<String, String> identityToName = new LinkedHashMap<>();

    private ShapeNaming(ShapeFileConfig config) {
        this.config = config;
    }

    /** The document's distinct objects in document order, named. */
    public static List<Named> assign(DocumentShape document, ShapeFileConfig config) {
        ShapeNaming naming = new ShapeNaming(config);
        for (YamlProperty property : document.topLevel().values()) {
            if ("objectClasses".equals(property.key()) && property.shape() instanceof MapShape map) {
                naming.collect(map.value(), OBJECT_CLASS_NAME);
            } else {
                naming.collect(property.shape(), null);
            }
        }
        for (String name : naming.shapes.keySet()) {
            if (!name.matches("[A-Za-z_][A-Za-z0-9_]*")) {
                throw new IllegalStateException("Invalid generated name '" + name
                        + "'; declare an explicit 'name' for that shape in the shape file");
            }
        }
        return new ArrayList<>(naming.shapes.values()).stream().map(s -> new Named(s, naming.nameOf(s))).toList();
    }

    public record Named(ObjectShape shape, String name) {
    }

    private void collect(YamlShape shape, String forcedName) {
        switch (shape) {
            case ObjectShape o -> register(o, forcedName);
            case ListShape l -> collect(l.item(), null);
            case MapShape m -> collect(m.value(), null);
            case UnionShape u -> u.alternatives().forEach(alt -> collect(alt, null));
            default -> {
            }
        }
    }

    private void register(ObjectShape shape, String forcedName) {
        String identity = ShapeIdentity.of(shape);
        if (identityToName.containsKey(identity)) {
            return;
        }
        // The value of the objectClasses map is the per-object-class union — always named ObjectClass.
        String name = OBJECT_CLASS_NAME.equals(forcedName)
                ? OBJECT_CLASS_NAME
                : uniqueName(baseName(shape), identity);
        // Registered before recursing, so a cyclic binding (a builder that sub-builds itself) terminates.
        identityToName.put(identity, name);
        shapes.put(name, shape);
        for (YamlProperty property : shape.properties().values()) {
            collect(property.shape(), null);
        }
    }

    private String nameOf(ObjectShape shape) {
        return identityToName.get(ShapeIdentity.of(shape));
    }

    /**
     * The base name: the DSL node's explicit name, the shape-file override, the builder type's
     * simple name (Impl/Builder stripped), or ObjectClass.
     */
    private String baseName(ObjectShape shape) {
        if (shape.name() != null) {
            return shape.name();
        }
        String sourceType = shape.sourceType();
        String override = sourceType == null ? null : config.nameFor(sourceType);
        if (override != null) {
            return override;
        }
        if (sourceType == null || sourceType.isEmpty()) {
            return OBJECT_CLASS_NAME;
        }
        String simple = sourceType.substring(sourceType.lastIndexOf('$') + 1);
        simple = simple.substring(simple.lastIndexOf('.') + 1);
        if (simple.endsWith("Impl")) {
            simple = simple.substring(0, simple.length() - "Impl".length());
        }
        if (simple.endsWith("Builder")) {
            simple = simple.substring(0, simple.length() - "Builder".length());
        }
        // A shape-file position can leak into the name (a declared object with no name); a
        // position is a location path (…properties.supportedFilters[*]) — keep the identifier prefix.
        int cut = simple.length();
        for (int i = 0; i < simple.length(); i++) {
            char c = simple.charAt(i);
            if (!Character.isLetterOrDigit(c) && c != '_') {
                cut = i;
                break;
            }
        }
        simple = simple.substring(0, cut);
        return simple.isEmpty() ? OBJECT_CLASS_NAME : simple;
    }

    /** The name is free, or a suffix is appended (the caller only reaches here with a new structure). */
    private String uniqueName(String base, String identity) {
        if (!shapes.containsKey(base)) {
            return base;
        }
        int suffix = 2;
        while (shapes.containsKey(base + suffix)) {
            suffix++;
        }
        return base + suffix;
    }
}
