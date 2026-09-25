/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.devtools.yaml.codegen.jsonschema;

import com.evolveum.polygon.conndev.devtools.yaml.codegen.ShapeNaming;
import com.evolveum.polygon.conndev.devtools.yaml.config.ShapeFileConfig;
import com.evolveum.polygon.conndev.devtools.yaml.shape.AnyShape;
import com.evolveum.polygon.conndev.devtools.yaml.shape.DelegatedObjectShape;
import com.evolveum.polygon.conndev.devtools.yaml.shape.DocumentShape;
import com.evolveum.polygon.conndev.devtools.yaml.shape.LeafShape;
import com.evolveum.polygon.conndev.devtools.yaml.shape.ListShape;
import com.evolveum.polygon.conndev.devtools.yaml.shape.MapShape;
import com.evolveum.polygon.conndev.devtools.yaml.shape.ObjectShape;
import com.evolveum.polygon.conndev.devtools.yaml.shape.PathShape;
import com.evolveum.polygon.conndev.devtools.yaml.shape.ScriptShape;
import com.evolveum.polygon.conndev.devtools.yaml.shape.ShapeIdentity;
import com.evolveum.polygon.conndev.devtools.yaml.shape.UnionShape;
import com.evolveum.polygon.conndev.devtools.yaml.shape.YamlProperty;
import com.evolveum.polygon.conndev.devtools.yaml.shape.YamlShape;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Renders the document as a JSONSchema (draft 2020-12) for validation: a closed, strict schema —
 * {@code additionalProperties: false} on every object, no {@code null} unless the shape declares
 * it, enums as {@code enum}, Groovy leaves as strings with {@code x-script} metadata. Repeated
 * sub-objects are deduplicated into {@code $defs} (one per distinct structure) and referenced by
 * {@code $ref}.
 */
public final class JsonSchemaGenerator {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private final DocumentShape document;
    private final Map<String, ObjectShape> defs = new LinkedHashMap<>();
    private final Map<String, String> identityToName = new LinkedHashMap<>();

    public JsonSchemaGenerator(DocumentShape document, ShapeFileConfig config) {
        this.document = document;
        for (ShapeNaming.Named entry : ShapeNaming.assign(document, config)) {
            defs.put(entry.name(), entry.shape());
            identityToName.put(ShapeIdentity.of(entry.shape()), entry.name());
        }
    }

    public String generate() {
        ObjectNode root = orderedObject();
        root.put("$schema", "https://json-schema.org/draft/2020-12/schema");
        root.put("title", document.name() + " connector YAML document");
        root.put("description", "Generated from the @Yaml.*-annotated entry builders — " + document.generatedBy()
                + ". Do not edit by hand.");
        root.put("type", "object");
        ObjectNode properties = root.putObject("properties");
        List<String> required = new ArrayList<>();
        for (YamlProperty property : document.topLevel().values()) {
            properties.set(property.key(), propertySchema(property));
            if (property.required() || document.requiredTopLevel().contains(property.key())) {
                required.add(property.key());
            }
        }
        root.set("additionalProperties", falseNode());
        if (!required.isEmpty()) {
            root.set("required", stringArray(required));
        }
        if (!defs.isEmpty()) {
            ObjectNode defNodes = root.putObject("$defs");
            for (Map.Entry<String, ObjectShape> def : defs.entrySet()) {
                defNodes.set(def.getKey(), objectDef(def.getValue()));
            }
        }
        try {
            return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(root);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot serialize the JSONSchema", e);
        }
    }

    private String nameOf(ObjectShape shape) {
        return identityToName.get(ShapeIdentity.of(shape));
    }

    private ObjectNode propertySchema(YamlProperty property) {
        ObjectNode node = schemaNode(property.shape());
        if (property.description() != null) {
            node.put("description", property.description());
        }
        if (property.deprecated()) {
            node.put("deprecated", true);
        }
        if (property.nullable()) {
            node = withNull(node);
        }
        return node;
    }

    private ObjectNode schemaNode(YamlShape shape) {
        switch (shape) {
            case ObjectShape o -> {
                ObjectNode node = orderedObject();
                node.put("$ref", "#/$defs/" + nameOf(o));
                return node;
            }
            case ListShape l -> {
                ObjectNode node = orderedObject();
                node.put("type", "array");
                node.set("items", schemaNode(l.item()));
                return node;
            }
            case MapShape m -> {
                ObjectNode node = orderedObject();
                node.put("type", "object");
                node.set("additionalProperties", m.nullableValue() ? withNull(schemaNode(m.value())) : schemaNode(m.value()));
                if (!m.restrictedKeys().isEmpty()) {
                    ObjectNode propertyNames = node.putObject("propertyNames");
                    propertyNames.set("enum", stringArray(m.restrictedKeys()));
                }
                return node;
            }
            case UnionShape u -> {
                ObjectNode node = orderedObject();
                ArrayNode oneOf = node.putArray("oneOf");
                for (YamlShape alternative : u.alternatives()) {
                    oneOf.add(schemaNode(alternative));
                }
                return node;
            }
            case LeafShape l -> {
                ObjectNode node = orderedObject();
                node.put("type", switch (l.type()) {
                    case STRING -> "string";
                    case BOOLEAN -> "boolean";
                    case INTEGER -> "integer";
                    case NUMBER -> "number";
                });
                if (!l.enumValues().isEmpty()) {
                    node.set("enum", stringArray(l.enumValues()));
                }
                if (l.defaultValue() != null) {
                    node.put("default", l.defaultValue());
                }
                return node;
            }
            case ScriptShape s -> {
                ObjectNode node = orderedObject();
                node.put("type", "string");
                ObjectNode script = node.putObject("x-script");
                script.put("kind", s.expression() ? "expression" : "block");
                if (s.emptyBody()) {
                    script.put("emptyBody", true);
                }
                return node;
            }
            case PathShape p -> {
                ObjectNode node = orderedObject();
                ArrayNode oneOf = node.putArray("oneOf");
                ObjectNode scalar = oneOf.addObject();
                scalar.put("type", "string");
                ObjectNode mapping = oneOf.addObject();
                mapping.put("type", "object");
                ObjectNode mappingProperties = mapping.putObject("properties");
                ObjectNode typeProp = mappingProperties.putObject("type");
                typeProp.put("type", "string");
                typeProp.put("description", "The attribute path format name (e.g. JSON_PATH, JSON_POINTER, SCIM)");
                ObjectNode valueProp = mappingProperties.putObject("value");
                valueProp.put("type", "string");
                mapping.set("required", stringArray(List.of("value")));
                mapping.set("additionalProperties", falseNode());
                return node;
            }
            case AnyShape a -> {
                return orderedObject(); // no constraints
            }
            case DelegatedObjectShape d -> throw new IllegalStateException(
                    "Unresolved DelegatedObjectShape reached the generator: " + d.source());
        }
    }

    private ObjectNode objectDef(ObjectShape shape) {
        ObjectNode node = orderedObject();
        if (shape.description() != null) {
            node.put("description", shape.description());
        }
        node.put("type", "object");
        ObjectNode properties = node.putObject("properties");
        List<String> required = new ArrayList<>();
        for (YamlProperty property : shape.properties().values()) {
            properties.set(property.key(), propertySchema(property));
            if (property.required()) {
                required.add(property.key());
            }
        }
        node.set("additionalProperties", falseNode());
        if (!required.isEmpty()) {
            node.set("required", stringArray(required));
        }
        if (!shape.sourceType().isEmpty()) {
            node.put("x-conndev-source", shape.sourceType());
        }
        return node;
    }

    /** Wraps a schema so that an explicit null is also accepted. */
    private ObjectNode withNull(ObjectNode node) {
        if (node.has("type") && node.get("type").isTextual()) {
            ObjectNode wrapper = orderedObject();
            ArrayNode type = wrapper.putArray("type");
            type.add(node.get("type").asText());
            type.add("null");
            copyRemaining(node, wrapper);
            return wrapper;
        }
        ObjectNode wrapper = orderedObject();
        ArrayNode anyOf = wrapper.putArray("anyOf");
        anyOf.add(node);
        ObjectNode nullType = anyOf.addObject();
        nullType.put("type", "null");
        return wrapper;
    }

    private void copyRemaining(ObjectNode from, ObjectNode to) {
        for (Map.Entry<String, JsonNode> field : from.properties()) {
            if (!"type".equals(field.getKey()) && !to.has(field.getKey())) {
                to.set(field.getKey(), field.getValue());
            }
        }
    }

    private ObjectNode orderedObject() {
        return MAPPER.createObjectNode();
    }

    private JsonNode falseNode() {
        return MAPPER.valueToTree(false);
    }

    private ArrayNode stringArray(List<String> values) {
        ArrayNode array = MAPPER.createArrayNode();
        for (String value : values) {
            array.add(value);
        }
        return array;
    }
}
