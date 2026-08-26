/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.json;

import com.evolveum.polygon.conndev.api.AttributePath;
import com.evolveum.polygon.conndev.concepts.Path;
import com.evolveum.polygon.conndev.spi.AttributeProtocolMapping;
import com.evolveum.polygon.conndev.spi.ValueMapping;
import org.identityconnectors.framework.common.objects.Attribute;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.*;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

public class JsonAttributeMapping implements AttributeProtocolMapping<ObjectNode, JsonNode> {

    protected final AttributePath path;
    protected final ValueMapping<Object, JsonNode> valueMapping;

    public JsonAttributeMapping(String name,
                                ValueMapping<Object, JsonNode> valueMapping) {
        this(AttributePath.of(name), valueMapping);
    }

    public JsonAttributeMapping(AttributePath path,
                                ValueMapping<Object, JsonNode> valueMapping) {
        this.path = path;
        this.valueMapping = valueMapping;
    }


    /**
     * Resolver for attribute path components that resolves JSON values in a nullable manner.
     *
     * When the previously resolved value is null, the resolver returns null. For a non-null
     * previously resolved value, attribute and extension components are resolved by name,
     * index components are resolved by index, and simple value filters are resolved by locating
     * the first matching element in an array node, or by matching the filter against an object
     * node itself.
     */
    public static final Path.Resolver<JsonNode, AttributePath.Component> NULLABLE_PATH_RESOLVER = (resolved, previous, next) -> {
        if (previous == null) {
            return null;
        }

        return switch (next) {
            case AttributePath.Attribute attr -> previous.get(attr.name());
            case AttributePath.Extension extension -> previous.get(extension.name());
            case AttributePath.IndexFilter filter -> previous.size() > filter.index() ? previous.get(filter.index()) : null;
            case AttributePath.SimpleValueFilter valueFilter -> applyValueFilter(previous, valueFilter);
        };
    };

    private static JsonNode applyValueFilter(JsonNode node, AttributePath.SimpleValueFilter filter) {
        if (node instanceof ArrayNode arrayNode) {
            return arrayNode.valueStream()
                    .filter(v -> matches(filter, v))
                    .findFirst()
                    .orElse(null);
        }
        if (node instanceof ObjectNode objectNode && matches(filter, objectNode)) {
            return objectNode;
        }
        return null;
    }


    @Override
    public Class<?> connIdType() {
        return valueMapping.connIdType();
    }

    @Override
    public JsonNode attributeFromObject(ObjectNode object) {
        if (path != null) {
            return path.resolve(object, NULLABLE_PATH_RESOLVER);
        }
        return null;
    }

    @Override
    public Object singleValueFromAttribute(JsonNode attribute) {
        return valueMapping.toConnIdValue(attribute);
    }

    @Override
    public List<Object> valuesFromAttribute(JsonNode attribute) {
        if (attribute instanceof ArrayNode arrayNode) {
            var ret = new ArrayList<>();
            for (var value : arrayNode) {
                ret.add(singleValueFromAttribute(value));
            }
            return ret;
        }
        var maybeVal = singleValueFromAttribute(attribute);
        if (maybeVal != null) {
            return List.of(maybeVal);
        }
        return null;
    }

    public void toJsonNode(Attribute attribute, ObjectNode parent) {
        var values = attribute.getValue().stream()
                .map(valueMapping::toWireValue)
                .toList();

        if (values.isEmpty()) {
            return;
        }

        var structuralPath = path.withoutFilters();
        var components = structuralPath.components();

        if (components.isEmpty()) {
            return;
        }

        ObjectNode current = parent;
        for (int i = 0; i < components.size() - 1; i++) {
            if (!(components.get(i) instanceof AttributePath.Attribute attr)) {
                return;
            }
            if (!current.has(attr.name()) || !current.get(attr.name()).isObject()) {
                current.putObject(attr.name());
            }
            current = current.withObject(attr.name());
        }

        var last = components.get(components.size() - 1);
        if (last instanceof AttributePath.Attribute attr) {
            JsonNode value = values.size() == 1
                    ? values.getFirst()
                    : parent.arrayNode().addAll(values);
            current.set(attr.name(), value);
        }
    }

    private static boolean matches(AttributePath.SimpleValueFilter filter, JsonNode node) {
        if (node instanceof ObjectNode objectNode) {
            for (var keyValue : filter.keyValues().entrySet()) {
                var filterVal = keyValue.getValue();
                var realNode = objectNode.get(keyValue.getKey());
                if (!matches(realNode, filterVal)) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    private static boolean matches(JsonNode realNode, Object filterVal) {
        if (realNode == null) {
            return false;
        }
        return switch (realNode) {
            case StringNode str -> filterVal.equals(str.asString());
            case NumericNode num -> filterVal instanceof Number filterNum && numbersMatch(num.numberValue(), filterNum);
            case BooleanNode bool -> filterVal.equals(bool.asBoolean());
            case ObjectNode objectNode -> filterVal instanceof AttributePath.SimpleValueFilter filter && matches(filter, objectNode.asObject());
            default -> false;
        };
    }

    private static boolean numbersMatch(Number jsonValue, Number filterValue) {
        if (isIntegral(jsonValue) && isIntegral(filterValue)) {
            return new BigDecimal(jsonValue.toString()).compareTo(new BigDecimal(filterValue.toString())) == 0;
        }
        return jsonValue.doubleValue() == filterValue.doubleValue();
    }

    private static boolean isIntegral(Number value) {
        return value instanceof Byte
                || value instanceof Short
                || value instanceof Integer
                || value instanceof Long
                || value instanceof BigInteger;
    }

    public AttributePath path() {
        return path;
    }
}
