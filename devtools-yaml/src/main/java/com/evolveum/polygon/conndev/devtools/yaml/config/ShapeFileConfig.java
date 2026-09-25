/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.devtools.yaml.config;

import com.evolveum.polygon.conndev.devtools.yaml.shape.YamlShape;

import java.util.List;
import java.util.Map;

/**
 * The parsed shape file of one connector project — the declarative input the generator needs
 * beside the scan: which entry builders drive the document (and under which top-level key / slot),
 * the shapes of the opaque {@code @Yaml.Custom} handlers and {@code @Yaml.ValueParser} coercers,
 * the loader-handled extra top-level entries, naming overrides and explicit-null allowances.
 */
public final class ShapeFileConfig {

    public static final String API_VERSION = "conndev-yaml-shape/v1";

    private final String document;
    private final String pythonModule;
    private final String pythonDocumentClass;
    private final List<EntryConfig> entries;
    private final Map<String, EntryConfig> extraEntries;
    private final List<HandlerConfig> customHandlers;
    private final Map<String, YamlShape> valueParsers;
    private final Map<String, String> names;
    private final List<String> nullable;
    private final Map<String, YamlShape> overrides;

    ShapeFileConfig(Builder b) {
        this.document = b.document;
        this.pythonModule = b.pythonModule;
        this.pythonDocumentClass = b.pythonDocumentClass;
        this.entries = List.copyOf(b.entries);
        this.extraEntries = Map.copyOf(b.extraEntries);
        this.customHandlers = List.copyOf(b.customHandlers);
        this.valueParsers = Map.copyOf(b.valueParsers);
        this.names = Map.copyOf(b.names);
        this.nullable = List.copyOf(b.nullable);
        this.overrides = Map.copyOf(b.overrides);
    }

    public String document() {
        return document;
    }

    /** The generated Python module file name (without extension). */
    public String pythonModule() {
        return pythonModule;
    }

    /** The generated top-level Pydantic class name (the midpilot contract). */
    public String pythonDocumentClass() {
        return pythonDocumentClass;
    }

    public List<EntryConfig> entries() {
        return entries;
    }

    /** The loader-handled top-level keys with no builder binding (e.g. {@code relationships}). */
    public Map<String, EntryConfig> extraEntries() {
        return extraEntries;
    }

    public List<HandlerConfig> customHandlers() {
        return customHandlers;
    }

    /** The declared shape of a {@code @Yaml.ValueParser} target, or {@code null}. */
    public YamlShape valueParserShape(String parserFqn) {
        return valueParsers.get(parserFqn);
    }

    /** An optional generated-class name override for a builder type. */
    public String nameFor(String typeFqn) {
        return names.get(typeFqn);
    }

    /** Dotted top-level paths (with {@code *} for map keys and list indices) where explicit nulls are allowed. */
    public List<String> nullablePaths() {
        return nullable;
    }

    /**
     * Shape replacements keyed by dotted top-level paths (with {@code *} for map keys and list
     * indices): the shape at a matching path is replaced by the declaration (a scan gap the
     * shape file has to reconcile, e.g. a map value type whose runtime instance accepts more
     * keys than the statically resolved type).
     */
    public Map<String, YamlShape> overrides() {
        return overrides;
    }

    public record EntryConfig(String key, String slot, String type, String binding, YamlShape shape, String description) {

        public EntryConfig(String key, String slot, String type, String binding, String description) {
            this(key, slot, type, binding, null, description);
        }

        /** Whether the value is a name map ({@code mapOf}, e.g. {@code objectClasses}) or a direct object ({@code object}). */
        public boolean mapOf() {
            return "mapOf".equals(binding);
        }
    }

    public record HandlerConfig(String handler, List<String> on, YamlShape shape) {

        /** The target types the declaration applies to; empty for "applies everywhere". */
        public boolean onEmpty() {
            return on.isEmpty();
        }
    }

    public static final class Builder {

        private String document;
        private String pythonModule = "connector_yaml_schema";
        private String pythonDocumentClass = "ConnectorYamlDocument";
        private final java.util.List<EntryConfig> entries = new java.util.ArrayList<>();
        private final java.util.LinkedHashMap<String, EntryConfig> extraEntries = new java.util.LinkedHashMap<>();
        private final java.util.List<HandlerConfig> customHandlers = new java.util.ArrayList<>();
        private final java.util.LinkedHashMap<String, YamlShape> valueParsers = new java.util.LinkedHashMap<>();
        private final java.util.LinkedHashMap<String, String> names = new java.util.LinkedHashMap<>();
        private final java.util.List<String> nullable = new java.util.ArrayList<>();
        private final java.util.LinkedHashMap<String, YamlShape> overrides = new java.util.LinkedHashMap<>();

        public Builder document(String document) {
            this.document = document;
            return this;
        }

        public Builder pythonModule(String pythonModule) {
            this.pythonModule = pythonModule;
            return this;
        }

        public Builder pythonDocumentClass(String pythonDocumentClass) {
            this.pythonDocumentClass = pythonDocumentClass;
            return this;
        }

        public Builder addEntry(EntryConfig entry) {
            entries.add(entry);
            return this;
        }

        public Builder addExtraEntry(String key, EntryConfig entry) {
            extraEntries.put(key, entry);
            return this;
        }

        public Builder addHandler(HandlerConfig handler) {
            customHandlers.add(handler);
            return this;
        }

        public Builder addValueParser(String fqn, YamlShape shape) {
            valueParsers.put(fqn, shape);
            return this;
        }

        public Builder addName(String fqn, String name) {
            names.put(fqn, name);
            return this;
        }

        public Builder addNullable(String path) {
            nullable.add(path);
            return this;
        }

        public Builder addOverride(String path, YamlShape shape) {
            overrides.put(path, shape);
            return this;
        }

        public ShapeFileConfig build() {
            if (document == null || document.isEmpty()) {
                throw new IllegalStateException("The shape file needs a 'document' name");
            }
            if (entries.isEmpty() && extraEntries.isEmpty()) {
                throw new IllegalStateException("The shape file needs at least one entry");
            }
            return new ShapeFileConfig(this);
        }
    }
}
