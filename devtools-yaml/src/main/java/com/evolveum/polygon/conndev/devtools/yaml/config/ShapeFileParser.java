/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.devtools.yaml.config;

import com.evolveum.polygon.conndev.devtools.yaml.config.ShapeFileConfig.EntryConfig;
import com.evolveum.polygon.conndev.devtools.yaml.config.ShapeFileConfig.HandlerConfig;
import com.evolveum.polygon.conndev.devtools.yaml.shape.ObjectShape;
import com.evolveum.polygon.conndev.devtools.yaml.shape.YamlShape;
import tools.jackson.databind.JsonNode;
import tools.jackson.dataformat.yaml.YAMLMapper;

import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Parses a connector project's shape file (YAML) into a {@link ShapeFileConfig}. The file is the
 * single declarative input beside the scan: it names the entry builders, declares the shapes of
 * the opaque {@code @Yaml.Custom} handlers and {@code @Yaml.ValueParser} coercers, the extra
 * loader-handled top-level entries, named shape replacements (overrides), class-name overrides
 * and explicit-null allowances.
 */
public final class ShapeFileParser {

    private static final YAMLMapper YAML = new YAMLMapper();

    private ShapeFileParser() {
    }

    public static ShapeFileConfig parse(Path file) {
        JsonNode root;
        try {
            root = YAML.readTree(file.toFile());
        } catch (tools.jackson.core.JacksonException | UncheckedIOException e) {
            throw new IllegalStateException("Cannot read shape file " + file + ": " + e.getMessage(), e);
        }
        if (root == null || !root.isObject()) {
            throw fileError(file, "the shape file must be a YAML mapping");
        }
        String apiVersion = text(root.get("apiVersion"));
        if (!ShapeFileConfig.API_VERSION.equals(apiVersion)) {
            throw fileError(file, "unsupported apiVersion '" + apiVersion + "' (expected " + ShapeFileConfig.API_VERSION + ")");
        }
        ShapeFileConfig.Builder config = new ShapeFileConfig.Builder()
                .document(requireText(root, "document"))
                .pythonModule(textOr(root, "pythonModule", "connector_yaml_schema"))
                .pythonDocumentClass(textOr(root, "pythonDocumentClass", "ConnectorYamlDocument"));

        JsonNode entries = root.get("entries");
        if (entries != null && entries.isArray()) {
            for (JsonNode entry : entries) {
                config.addEntry(parseEntry(entry, "entries", file));
            }
        }

        JsonNode extra = root.get("extraEntries");
        if (extra != null) {
            requireObject(extra, "extraEntries", file);
            for (var field : extra.properties()) {
                config.addExtraEntry(field.getKey(), parseExtraEntry(field.getValue(), "extraEntries." + field.getKey(), file));
            }
        }

        JsonNode handlers = root.get("customHandlers");
        if (handlers != null && handlers.isArray()) {
            for (JsonNode handler : handlers) {
                requireObject(handler, "customHandlers[]", file);
                String fqn = requireText(handler, "handler");
                List<String> on = new ArrayList<>();
                JsonNode onNode = handler.get("on");
                if (onNode != null) {
                    requireArray(onNode, "customHandlers[].on", file);
                    for (JsonNode item : onNode) {
                        on.add(text(item));
                    }
                }
                config.addHandler(new HandlerConfig(fqn, on,
                        namedHandlerShape(ShapeDsl.parse(requireNode(handler, "shape"), "customHandlers[" + fqn + "].shape"),
                                fqn, text(handler.get("name")))));
            }
        }

        JsonNode parsers = root.get("valueParsers");
        if (parsers != null) {
            requireObject(parsers, "valueParsers", file);
            for (var field : parsers.properties()) {
                config.addValueParser(field.getKey(), ShapeDsl.parse(field.getValue(), "valueParsers." + field.getKey()));
            }
        }

        JsonNode names = root.get("names");
        if (names != null) {
            requireObject(names, "names", file);
            for (var field : names.properties()) {
                config.addName(field.getKey(), requireTextValue(field.getValue(), "names." + field.getKey()));
            }
        }

        JsonNode nullable = root.get("nullable");
        if (nullable != null && nullable.isArray()) {
            for (JsonNode item : nullable) {
                config.addNullable(text(item));
            }
        }

        JsonNode overrides = root.get("overrides");
        if (overrides != null) {
            requireObject(overrides, "overrides", file);
            for (var field : overrides.properties()) {
                config.addOverride(field.getKey(),
                        ShapeDsl.parse(field.getValue(), "overrides." + field.getKey()));
            }
        }

        return config.build();
    }

    private static EntryConfig parseEntry(JsonNode node, String position, Path file) {
        requireObject(node, position, file);
        String key = requireText(node, "key");
        String binding = textOr(node, "binding", "object");
        if (!"object".equals(binding) && !"mapOf".equals(binding)) {
            throw fileError(file, position + ".binding: expected 'object' or 'mapOf', got '" + binding + "'");
        }
        String type = text(node.get("type"));
        YamlShape extraShape = null;
        JsonNode shapeNode = node.get("shape");
        if (shapeNode != null && !shapeNode.isNull()) {
            extraShape = ShapeDsl.parse(shapeNode, position + ".shape");
        }
        if (type == null && extraShape == null) {
            throw fileError(file, position + ": an entry needs a 'type' (the entry builder) or a declarative 'shape'");
        }
        return new EntryConfig(key, textOr(node, "slot", "default"), type, binding, extraShape, text(node.get("description")));
    }

    private static EntryConfig parseExtraEntry(JsonNode node, String position, Path file) {
        requireObject(node, position, file);
        YamlShape shape = ShapeDsl.parse(requireNode(node, "shape"), position + ".shape");
        return new EntryConfig(null, "extra", null, "object", shape, text(node.get("description")));
    }

    /**
     * The handler shape's generated name: the shape node's own {@code name} wins, then the
     * entry's {@code name}, then the handler class's simple name with the {@code Impl},
     * {@code Builder} and {@code Handler} suffixes stripped.
     */
    private static YamlShape namedHandlerShape(YamlShape shape, String handlerFqn, String entryName) {
        if (!(shape instanceof ObjectShape object) || object.name() != null) {
            return shape;
        }
        String name = entryName;
        if (name == null) {
            name = handlerFqn.substring(handlerFqn.lastIndexOf('.') + 1);
            for (String suffix : new String[] {"Impl", "Builder", "Handler"}) {
                if (name.endsWith(suffix)) {
                    name = name.substring(0, name.length() - suffix.length());
                }
            }
        }
        return object.withName(name);
    }

    private static String requireText(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual() || value.asText().isEmpty()) {
            throw new IllegalStateException("Missing required field '" + field + "'");
        }
        return value.asText();
    }

    private static String requireTextValue(JsonNode value, String position) {
        if (value == null || !value.isTextual() || value.asText().isEmpty()) {
            throw new IllegalStateException("Invalid shape file value at '" + position + "': expected a non-empty string");
        }
        return value.asText();
    }

    private static String textOr(JsonNode node, String field, String fallback) {
        JsonNode value = node.get(field);
        return (value != null && value.isTextual() && !value.asText().isEmpty()) ? value.asText() : fallback;
    }

    private static String text(JsonNode value) {
        return (value != null && value.isTextual()) ? value.asText() : null;
    }

    private static JsonNode requireNode(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            throw new IllegalStateException("Missing required field '" + field + "'");
        }
        return value;
    }

    private static void requireObject(JsonNode node, String position, Path file) {
        if (node == null || !node.isObject()) {
            throw fileError(file, position + ": must be a mapping");
        }
    }

    private static void requireArray(JsonNode node, String position, Path file) {
        if (node == null || !node.isArray()) {
            throw fileError(file, position + ": must be a list");
        }
    }

    private static IllegalStateException fileError(Path file, String detail) {
        return new IllegalStateException("Invalid shape file " + file + ": " + detail);
    }
}
