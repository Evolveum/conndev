/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.json;

import com.evolveum.polygon.conndev.concepts.GroovyClosures;
import com.evolveum.polygon.conndev.schema.AbstractValueMappingBuilder;
import com.evolveum.polygon.conndev.spi.ValueMapping;
import groovy.lang.Closure;
import tools.jackson.databind.JsonNode;

import java.util.Set;
import java.util.function.Function;

/**
 * Builder for JSON value mappings that composes with the mapping derived from the
 * declared JSON type and OpenAPI format.
 *
 * <p>The base mapping ({@link JsonValueMapping}, e.g. from
 * {@code OpenApiValueMapping.from(jsonType, openApiFormat)}) owns the wire encoding
 * and type metadata. Closures only transform ConnId values:</p>
 * <ul>
 *   <li>{@code serialize} receives the ConnId value and returns a ConnId value,
 *       the result is encoded to the declared wire type by the base mapping.
 *       Returning an explicit {@link JsonNode} is an escape hatch and passes through.</li>
 *   <li>{@code deserialize} receives the wire {@link JsonNode} and returns a ConnId value.</li>
 * </ul>
 *
 * <p>Directions without a configured closure fall back to the base mapping.</p>
 */
public class JsonValueMappingBuilder extends AbstractValueMappingBuilder<Object, JsonNode, JsonValueMappingBuilder> {

    private final JsonValueMapping base;

    public JsonValueMappingBuilder(JsonValueMapping base) {
        this.base = base;
    }

    @Override
    public JsonValueMappingBuilder serialize(Closure<JsonNode> closure) {
        this.serialize = value -> {
            Object result = GroovyClosures.copyAndCall(closure, new SerializationContext<>(value));
            if (result instanceof JsonNode node) {
                return node;
            }
            if (result == null) {
                return JsonValueMapping.NODE_FACTORY.nullNode();
            }
            return base.toWireValue(result);
        };
        return this;
    }

    /**
     * Builds the mapping. Directions without a closure fall back to the base mapping.
     *
     * @return the constructed value mapping
     */
    public ValueMapping<Object, JsonNode> build() {
        Function<JsonNode, Object> toConnId = deserialize != null ? deserialize : base::toConnIdValue;
        Function<Object, JsonNode> toWire = serialize != null ? serialize : base::toWireValue;
        return new JsonMappingImpl(base, toConnId, toWire);
    }

    private record JsonMappingImpl(JsonValueMapping base,
                                   Function<JsonNode, Object> toConnId,
                                   Function<Object, JsonNode> toWire)
            implements ValueMapping<Object, JsonNode> {

        @Override
        public Class<?> connIdType() {
            return base.connIdType();
        }

        @Override
        public Class<? extends JsonNode> primaryWireType() {
            return base.primaryWireType();
        }

        @Override
        public Set<Class<? extends JsonNode>> supportedWireTypes() {
            return base.supportedWireTypes();
        }

        @Override
        public JsonNode toWireValue(Object value) {
            return toWire.apply(value);
        }

        @Override
        public Object toConnIdValue(JsonNode value) {
            return toConnId.apply(value);
        }
    }
}
