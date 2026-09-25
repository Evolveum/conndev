/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.devtools.yaml.config;

import com.evolveum.polygon.conndev.devtools.yaml.shape.AnyShape;
import com.evolveum.polygon.conndev.devtools.yaml.shape.DelegatedObjectShape;
import com.evolveum.polygon.conndev.devtools.yaml.shape.LeafShape;
import com.evolveum.polygon.conndev.devtools.yaml.shape.ListShape;
import com.evolveum.polygon.conndev.devtools.yaml.shape.MapShape;
import com.evolveum.polygon.conndev.devtools.yaml.shape.ObjectShape;
import com.evolveum.polygon.conndev.devtools.yaml.shape.PathShape;
import com.evolveum.polygon.conndev.devtools.yaml.shape.ScriptShape;
import com.evolveum.polygon.conndev.devtools.yaml.shape.UnionShape;
import com.evolveum.polygon.conndev.devtools.yaml.shape.YamlProperty;
import com.evolveum.polygon.conndev.devtools.yaml.shape.YamlShape;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * The declarative shape DSL of the shape file, parsed into the {@link YamlShape} model. The DSL
 * describes exactly what the runtime binder accepts for a position the plain scan cannot derive:
 *
 * <pre>
 * type: object | list | map | oneOf | string | boolean | integer | number | script | path | any
 * properties: { &lt;key&gt;: &lt;shape&gt;, ... }        # object
 * required: [ &lt;key&gt;, ... ]                   # object
 * item: &lt;shape&gt;                              # list
 * value: &lt;shape&gt;                             # map
 * keys: [ &lt;key&gt;, ... ]                       # map, restrict the accepted entry keys (optional)
 * items: [ &lt;shape&gt;, ... ]                    # oneOf
 * enum: [ &lt;value&gt;, ... ]                     # string
 * default: &lt;value&gt;                           # leaf
 * expression: true                           # script (single build-time expression, not a closure body)
 * emptyBody: true                            # script
 * delegateTypes: [ &lt;FQN&gt;, ... ]              # object, merge the @Yaml.* bindings of these builder types
 * description: "..."
 * </pre>
 */
public final class ShapeDsl {

    /** The known shape kinds. */
    private static final List<String> TYPES = List.of(
            "object", "list", "map", "oneOf", "string", "boolean", "integer", "number", "script", "path", "any");

    // object options: properties, required, delegateTypes, inherit, name, description

    private ShapeDsl() {
    }

    /** Parses a DSL node into a shape; the node's location string is used in errors. */
    public static YamlShape parse(JsonNode node, String position) {
        requireObject(node, position);
        String type = requireText(node, "type", position);
        String description = optionalText(node, "description", position);
        String source = "shape-file:" + position;

        return switch (type) {
            case "object" -> parseObject(node, position, description);
            case "list" -> new ListShape(parse(parseNode(node, "item", position), position + "[*]"), source, description, false);
            case "map" -> parseMap(node, position, description);
            case "oneOf" -> {
                JsonNode itemsNode = node.get("items");
                if (itemsNode == null) {
                    throw dslError(position + ".items", "required for a oneOf shape");
                }
                List<YamlShape> items = new ArrayList<>();
                for (JsonNode item : requireArray(itemsNode, "items", position)) {
                    items.add(parse(item, position + "[]"));
                }
                yield new UnionShape(items, source, description, false);
            }
            case "string" -> new LeafShape(LeafShape.LeafType.STRING, enumList(node, position),
                    optionalText(node, "default", position), source, description, false);
            case "boolean" -> new LeafShape(LeafShape.LeafType.BOOLEAN, List.of(), null, source, description, false);
            case "integer" -> new LeafShape(LeafShape.LeafType.INTEGER, List.of(), null, source, description, false);
            case "number" -> new LeafShape(LeafShape.LeafType.NUMBER, List.of(), null, source, description, false);
            case "script" -> new ScriptShape(booleanOf(node, "expression", position), booleanOf(node, "emptyBody", position),
                    source, description, false);
            case "path" -> new PathShape(source, description, false);
            case "any" -> new AnyShape(source, description, false);
            default -> throw dslError(position, "unknown shape type '" + type + "' (expected one of " + String.join(", ", TYPES) + ")");
        };
    }

    private static YamlShape parseObject(JsonNode node, String position, String description) {
        LinkedHashMap<String, YamlProperty> properties = new LinkedHashMap<>();
        JsonNode props = node.get("properties");
        if (props != null) {
            requireObject(props, position + ".properties");
            for (var field : props.properties()) {
                properties.put(field.getKey(), YamlProperty.builder()
                        .key(field.getKey())
                        .shape(named(parse(field.getValue(), position + ".properties." + field.getKey()), field.getKey()))
                        .build());
            }
        }
        for (String required : stringList(node, "required", position)) {
            if (!properties.containsKey(required)) {
                throw dslError(position + ".required", "required key '" + required + "' is not a declared property");
            }
            properties.put(required, properties.get(required).toBuilder().required(true).build());
        }
        List<String> delegateTypes = new ArrayList<>(stringList(node, "delegateTypes", position));
        String inherit = optionalText(node, "inherit", position);
        if (inherit != null) {
            if (!delegateTypes.isEmpty()) {
                throw dslError(position, "'inherit' and 'delegateTypes' are mutually exclusive");
            }
            delegateTypes.add(inherit);
        }
        String name = optionalText(node, "name", position);
        if (!delegateTypes.isEmpty()) {
            // A single delegate (inherit) names the shape; several merged delegates do not.
            String sourceType = delegateTypes.size() == 1 ? delegateTypes.getFirst() : "shape-file:" + position;
            ObjectShape declared = new ObjectShape(properties, sourceType, null, false, name);
            return new DelegatedObjectShape(declared, delegateTypes, description, false);
        }
        return new ObjectShape(properties, "shape-file:" + position, description, false, name);
    }

    /**
     * An unnamed declared object nested under a property key takes the key as its generated name —
     * the shape-file position the object would otherwise derive its name from is a location path,
     * not a usable identifier.
     */
    private static YamlShape named(YamlShape shape, String key) {
        if (shape instanceof ObjectShape object && object.name() == null) {
            return object.withName(key);
        }
        if (shape instanceof ListShape list) {
            YamlShape renamedItem = named(list.item(), key);
            if (renamedItem != list.item()) {
                return new ListShape(renamedItem, list.source(), list.description(), list.deprecated());
            }
            return shape;
        }
        if (shape instanceof MapShape map && map.value() instanceof ObjectShape value && value.name() == null) {
            return new MapShape(value.withName(key), map.restrictedKeys(), map.source(), map.description(), map.deprecated());
        }
        if (shape instanceof UnionShape union) {
            List<ObjectShape> unnamed = new ArrayList<>();
            for (YamlShape alternative : union.alternatives()) {
                if (alternative instanceof ObjectShape object && object.name() == null) {
                    unnamed.add(object);
                }
            }
            if (unnamed.size() == 1) {
                ObjectShape theOne = unnamed.getFirst();
                List<YamlShape> renamed = new ArrayList<>();
                for (YamlShape alternative : union.alternatives()) {
                    renamed.add(alternative == theOne ? theOne.withName(key) : alternative);
                }
                return new UnionShape(renamed, union.source(), union.description(), union.deprecated());
            }
        }
        return shape;
    }

    private static YamlShape parseMap(JsonNode node, String position, String description) {
        List<String> restricted = stringList(node, "keys", position);
        MapShape map = new MapShape(parse(parseNode(node, "value", position), position + ".value"),
                restricted, "shape-file:" + position, description, false);
        return booleanOf(node, "nullableValue", position) ? map.withNullableValue() : map;
    }

    private static JsonNode parseNode(JsonNode node, String field, String position) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            throw dslError(position, "missing required field '" + field + "'");
        }
        return value;
    }

    private static void requireObject(JsonNode node, String position) {
        if (node == null || node.isTextual()) {
            // A textual node where a shape mapping is expected is almost always a YAML alias
            // (anchor/alias references do not resolve through the shape file's parser).
            throw dslError(position, node == null ? "missing"
                    : "found the scalar '" + node.asText() + "' - YAML anchors/aliases are not supported, repeat the shape");
        }
        if (!node.isObject()) {
            throw dslError(position, "a shape must be a mapping with a 'type'");
        }
    }

    private static String requireText(JsonNode node, String field, String position) {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual() || value.asText().isEmpty()) {
            throw dslError(position, "missing required field '" + field + "'");
        }
        return value.asText();
    }

    private static String optionalText(JsonNode node, String field, String position) {
        JsonNode value = node.get(field);
        return (value != null && value.isTextual()) ? value.asText() : null;
    }

    private static List<String> enumList(JsonNode node, String position) {
        List<String> result = new ArrayList<>();
        JsonNode value = node.get("enum");
        if (value != null) {
            for (JsonNode item : requireArray(value, "enum", position)) {
                if (!item.isValueNode()) {
                    throw dslError(position + ".enum", "enum values must be scalars");
                }
                result.add(item.asText());
            }
        }
        return result;
    }

    private static List<String> stringList(JsonNode node, String field, String position) {
        List<String> result = new ArrayList<>();
        JsonNode value = node.get(field);
        if (value == null) {
            return result;
        }
        for (JsonNode item : requireArray(value, field, position)) {
            if (!item.isValueNode()) {
                throw dslError(position + "." + field, "entries must be scalars");
            }
            result.add(item.asText());
        }
        return result;
    }

    private static boolean booleanOf(JsonNode node, String field, String position) {
        JsonNode value = node.get(field);
        if (value == null) {
            return false;
        }
        if (!value.isBoolean()) {
            throw dslError(position + "." + field, "must be a boolean");
        }
        return value.asBoolean();
    }

    private static tools.jackson.databind.node.ArrayNode requireArray(JsonNode node, String field, String position) {
        if (!node.isArray()) {
            throw dslError(position + "." + field, "must be a list");
        }
        return (tools.jackson.databind.node.ArrayNode) node;
    }

    private static IllegalStateException dslError(String position, String detail) {
        return new IllegalStateException("Invalid shape at '" + position + "': " + detail);
    }
}
