/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.json;

import com.evolveum.polygon.conndev.api.AttributePath;
import com.evolveum.polygon.conndev.api.AttributePathDeclaration;
import com.evolveum.polygon.conndev.api.JavaPathFormat;
import com.evolveum.polygon.conndev.api.ParsingException;
import com.evolveum.polygon.conndev.concepts.DefinitionValue;
import com.evolveum.polygon.conndev.concepts.Path;
import com.evolveum.polygon.conndev.spi.AttributeProtocolMapping;
import com.evolveum.polygon.conndev.spi.ValueMapping;
import org.identityconnectors.framework.common.exceptions.InvalidAttributeValueException;
import org.identityconnectors.framework.common.objects.Attribute;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.*;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

public class JsonAttributeMapping implements AttributeProtocolMapping<ObjectNode, JsonNode> {

    protected final AttributePathDeclaration<?, ?> pathDeclaration;
    protected final ValueMapping<Object, JsonNode> valueMapping;
    private final AttributePath path;
    protected final boolean multiValued;

    public JsonAttributeMapping(String name,
                                ValueMapping<Object, JsonNode> valueMapping) {
        this(AttributePathDeclaration.of(
                DefinitionValue.defaultFrom(JavaPathFormat.INSTANCE),
                DefinitionValue.defaultFrom(AttributePath.of(name))), valueMapping);
    }

    /**
     * Creates a mapping for the given pre-built path. A {@code null} path disables
     * path resolution: {@link #attributeFromObject(ObjectNode)} returns {@code null}.
     *
     * @param path the pre-built path, or {@code null} to disable path resolution
     * @param valueMapping the value mapping
     */
    public JsonAttributeMapping(AttributePath path,
                                ValueMapping<Object, JsonNode> valueMapping) {
        this(path, valueMapping, false);
    }

    /**
     * Creates a mapping for the given pre-built path, with an explicit multivalue flag.
     *
     * @param path the pre-built path, or {@code null} to disable path resolution
     * @param valueMapping the value mapping
     * @param multiValued whether the ConnId attribute this mapping serves is multivalued
     */
    public JsonAttributeMapping(AttributePath path,
                                ValueMapping<Object, JsonNode> valueMapping,
                                boolean multiValued) {
        this(path == null ? null : AttributePathDeclaration.of(JavaPathFormat.INSTANCE, path), valueMapping, multiValued);
    }

    /**
     * Creates a mapping from the given path declaration.
     *
     * <p>For string-based declarations the expression is parsed lazily: an invalid
     * expression surfaces as a {@link ParsingException} when the path is first
     * resolved (e.g. during {@link #attributeFromObject(ObjectNode)}), not here.</p>
     *
     * @param pathDeclaration the path declaration, or {@code null} to disable path resolution
     * @param valueMapping the value mapping
     */
    public JsonAttributeMapping(AttributePathDeclaration<?, ?> pathDeclaration,
                                ValueMapping<Object, JsonNode> valueMapping) {
        this(pathDeclaration, valueMapping, false);
    }

    /**
     * Creates a mapping from the given path declaration, with an explicit multivalue flag.
     *
     * @param pathDeclaration the path declaration, or {@code null} to disable path resolution
     * @param valueMapping the value mapping
     * @param multiValued whether the ConnId attribute this mapping serves is multivalued -
     *                    determines whether {@link #toJsonNode(Attribute, ObjectNode)} emits
     *                    a single value as a one-element array or as a scalar node
     */
    public JsonAttributeMapping(AttributePathDeclaration<?, ?> pathDeclaration,
                                ValueMapping<Object, JsonNode> valueMapping,
                                boolean multiValued) {
        this.pathDeclaration = pathDeclaration;
        this.valueMapping = valueMapping;
        this.path = pathDeclaration != null ? pathDeclaration.actual() : null;
        this.multiValued = multiValued;
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
        if (pathDeclaration != null) {
            return pathDeclaration.actual().resolve(object, NULLABLE_PATH_RESOLVER);
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
        var attributeValues = attribute.getValue();
        List<JsonNode> values = attributeValues == null ? List.of() : attributeValues.stream()
                .map(valueMapping::toWireValue)
                .toList();

        // An explicitly empty multivalued attribute clears the remote collection.
        if (values.isEmpty() && !multiValued) {
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

        var last = components.getLast();
        if (last instanceof AttributePath.Attribute attr) {
            current.set(attr.name(), singleOrArrayNode(attribute.getName(), values, parent));
        }
    }

    /**
     * Returns the given wire values as an array node (including an empty array) if
     * {@link #multiValued}, or as a single scalar node otherwise.
     *
     * @throws InvalidAttributeValueException if more than one value is given for a mapping that
     *                                is not declared multivalued - the schema and the actual
     *                                attribute values disagree, which is a bug upstream, not
     *                                something to silently paper over by emitting an array anyway
     */
    protected final JsonNode singleOrArrayNode(String attributeName, List<JsonNode> values, ObjectNode parent) {
        if (multiValued) {
            return parent.arrayNode().addAll(values);
        }
        if (values.size() > 1) {
            throw new InvalidAttributeValueException(
                    "Attribute '" + attributeName + "' is not declared multivalued but has " + values.size() + " values");
        }
        return values.getFirst();
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
        if (filterVal == null) {
            return realNode instanceof NullNode;
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

    /**
     * Returns the resolved attribute path of this mapping.
     *
     * @return the parsed {@link AttributePath}, or {@code null} if path resolution is disabled
     */
    public AttributePath path() {
        return pathDeclaration == null ? null : pathDeclaration.actual();
    }

    /**
     * Returns the path declaration as configured, keeping the user-provided value
     * (a path expression or a pre-built path), its format, and its source location
     * for error reports.
     *
     * @return the path declaration, or {@code null} if path resolution is disabled
     */
    public AttributePathDeclaration<?, ?> pathDeclaration() {
        return pathDeclaration;
    }
}
