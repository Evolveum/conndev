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
import com.evolveum.polygon.conndev.api.PathTypeException;
import com.evolveum.polygon.conndev.concepts.DefinitionValue;
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

    protected final AttributePathDeclaration<?, ?> pathDeclaration;
    protected final ValueMapping<Object, JsonNode> valueMapping;

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
        this(path == null ? null : AttributePathDeclaration.of(JavaPathFormat.INSTANCE, path), valueMapping);
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
        this.pathDeclaration = pathDeclaration;
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


    /**
     * Creates a resolver that ensures the container hierarchy of a path exists, creating missing nodes.
     *
     * The path passed to this resolver should contain only the container part of the attribute path —
     * the path up to but not including the final value attribute. For the attribute path
     * {@code user.name}, the resolver walks {@code user} and returns the {@code user} object node;
     * the value attribute itself ({@code name}) is then created by the caller in the returned node.
     *
     * Missing nodes are created as the walk proceeds: attribute and extension components create object
     * nodes, and index filter and value filter components require array nodes. An empty object node
     * created by the previous step of the same walk is replaced by the required array node in its
     * parent. A node populated by a preceding value filter step is never replaced; a following index
     * or value filter component that requires an array raises a {@link PathTypeException}. Existing
     * nodes are never modified or removed; a JSON {@code null} node is treated as missing and replaced.
     *
     * A value filter that finds no matching element in an array node creates a new object node populated
     * with the filter key/value pairs and appends it to the array node.
     *
     * A {@link PathTypeException} is thrown when an existing node does not have the type required by a
     * path component. Each call returns a new resolver instance; its state is specific to a single path walk.
     *
     * <p>The walk is not atomic: if a {@link PathTypeException} is thrown at a later component, the nodes
     * created by the preceding components remain in the document.
     *
     * @return the resolver that creates the missing nodes of the container path
     */
    public static Path.CheckedResolver<JsonNode, AttributePath.Component, PathTypeException> creatingResolver() {
        return new CreatingPathResolver();
    }

    private static final class CreatingPathResolver
            implements Path.CheckedResolver<JsonNode, AttributePath.Component, PathTypeException> {

        private JsonNode lastCreated;
        private Placement lastCreatedPlacement;

        private record Placement(JsonNode parent, String name, int index) {

            void replace(JsonNode current, JsonNode replacement) {
                if (parent instanceof ObjectNode objectNode) {
                    objectNode.set(name, replacement);
                } else {
                    ((ArrayNode) parent).set(index, replacement);
                }
            }
        }

        @Override
        public JsonNode resolve(List<Path.ResolvedPair<AttributePath.Component, JsonNode>> resolvedPath,
                                JsonNode previous, AttributePath.Component next) throws PathTypeException {
            if (resolvedPath.isEmpty()) {
                lastCreated = null;
                lastCreatedPlacement = null;
            }
            if (previous == null) {
                return null;
            }
            return switch (next) {
                case AttributePath.Attribute attribute -> createChild(previous, attribute.name(), next);
                case AttributePath.Extension extension -> createChild(previous, extension.name(), next);
                case AttributePath.IndexFilter indexFilter -> createAtIndex(previous, indexFilter.index(), next);
                case AttributePath.SimpleValueFilter valueFilter -> createMatching(previous, valueFilter, next);
            };
        }

        private JsonNode createChild(JsonNode previous, String name, AttributePath.Component component) throws PathTypeException {
            if (!(previous instanceof ObjectNode parent)) {
                throw typeMismatch(previous, "object", component);
            }
            var existing = parent.get(name);
            if (existing != null && !existing.isNull()) {
                lastCreated = null;
                lastCreatedPlacement = null;
                return existing;
            }
            var created = JsonNodeFactory.instance.objectNode();
            parent.set(name, created);
            lastCreated = created;
            lastCreatedPlacement = new Placement(parent, name, -1);
            return created;
        }

        private JsonNode createAtIndex(JsonNode previous, int index, AttributePath.Component component) throws PathTypeException {
            if (index < 0) {
                throw new PathTypeException("Path component '" + AttributePath.of(component)
                        + "': array index must not be negative: " + index);
            }
            if (previous instanceof ArrayNode array) {
                if (index < array.size() && !array.get(index).isNull()) {
                    lastCreated = null;
                    lastCreatedPlacement = null;
                    return array.get(index);
                }
                if (index < array.size()) {
                    array.set(index, JsonNodeFactory.instance.objectNode());
                }
                while (array.size() <= index) {
                    array.add(JsonNodeFactory.instance.objectNode());
                }
                lastCreated = array.get(index);
                lastCreatedPlacement = new Placement(array, null, index);
                return lastCreated;
            }
            if (isRetypableEmptyObject(previous)) {
                var array = retypedArray(previous);
                while (array.size() <= index) {
                    array.add(JsonNodeFactory.instance.objectNode());
                }
                lastCreated = array.get(index);
                lastCreatedPlacement = new Placement(array, null, index);
                return lastCreated;
            }
            throw typeMismatch(previous, "array", component);
        }

        private JsonNode createMatching(JsonNode previous, AttributePath.SimpleValueFilter filter,
                                        AttributePath.Component component) throws PathTypeException {
            var existing = applyValueFilter(previous, filter);
            if (existing != null) {
                lastCreated = null;
                lastCreatedPlacement = null;
                return existing;
            }
            ArrayNode array;
            if (previous instanceof ArrayNode existingArray) {
                array = existingArray;
            } else if (isRetypableEmptyObject(previous)) {
                array = retypedArray(previous);
            } else {
                throw typeMismatch(previous, "array", component);
            }
            var created = JsonNodeFactory.instance.objectNode();
            fill(created, filter);
            array.add(created);
            lastCreated = created;
            lastCreatedPlacement = new Placement(array, null, array.size() - 1);
            return created;
        }

        private boolean isRetypableEmptyObject(JsonNode node) {
            return node == lastCreated && lastCreatedPlacement != null
                    && node instanceof ObjectNode objectNode && objectNode.isEmpty();
        }

        private ArrayNode retypedArray(JsonNode previous) {
            var array = JsonNodeFactory.instance.arrayNode();
            lastCreatedPlacement.replace(previous, array);
            lastCreated = null;
            lastCreatedPlacement = null;
            return array;
        }

        private PathTypeException typeMismatch(JsonNode node, String expectedType, AttributePath.Component component) {
            return new PathTypeException("Path component '" + AttributePath.of(component).toString()
                    + "': expected " + expectedType + " node, found " + node.getNodeType());
        }
    }

    private static void fill(ObjectNode node, AttributePath.SimpleValueFilter filter) {
        for (var entry : filter.keyValues().entrySet()) {
            var value = entry.getValue();
            switch (value) {
                case null -> node.putNull(entry.getKey());
                case String string -> node.put(entry.getKey(), string);
                case Boolean bool -> node.put(entry.getKey(), bool);
                case BigInteger bigInteger -> node.put(entry.getKey(), bigInteger);
                case BigDecimal bigDecimal -> node.put(entry.getKey(), bigDecimal);
                case Number number when isIntegral(number) ->
                    node.put(entry.getKey(), number.longValue());
                case Number number -> {
                    node.put(entry.getKey(), number.doubleValue());
                }
                case AttributePath.SimpleValueFilter nested -> {
                    var child = JsonNodeFactory.instance.objectNode();
                    fill(child, nested);
                    node.set(entry.getKey(), child);
                }
                default -> throw new IllegalStateException("Unsupported value type in path value filter: " + value);
            }
        }
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
        var values = attribute.getValue().stream().map(valueMapping::toWireValue).toList();
        // FIXME: Add support for deep paths
        if (values.isEmpty()) {
            return;
        }
        var name = pathDeclaration.actual().onlyAttribute();
        parent.set(name.name(),values.size() == 1 ? values.getFirst() : parent.arrayNode().addAll(values));

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
