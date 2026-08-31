/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.schema;

import com.evolveum.polygon.conndev.spi.ValueMapping;

import java.util.function.Function;

/**
 * Builder for creating {@link ValueMapping} instances that define conversion between
 * ConnId and protocol (wire) types in a Groovy-based schema definition DSL.
 * <p>
 * This builder accepts closures for deserialization (protocol-to-ConnId) and serialization
 * (ConnId-to-protocol) functions, and builds an immutable {@link ValueMapping} that the
 * connector runtime uses for type conversion.
 *
 * @param <C> the ConnId value type
 * @param <P> the protocol (wire) value type
 */
public class BaseValueMappingBuilder<C,P>
        extends AbstractValueMappingBuilder<C, P, BaseValueMappingBuilder<C, P>> {

    /** The ConnId value class for this mapping. */
    private final Class<C> connIdType;

    /** The protocol (wire) value class for this mapping. */
    private final Class<P> protocolType;

    public BaseValueMappingBuilder(Class<C> connIdType, Class<P> protocolType) {
        this.connIdType = connIdType;
        this.protocolType = protocolType;
    }

    /**
     * Builds an immutable {@link ValueMapping} from the configured types and functions.
     *
     * @return the constructed value mapping
     */
    public ValueMapping<C,P> build() {
        return new ValueMappingImpl<>(connIdType, protocolType, deserialize, serialize);
    }

    /**
     * Immutable value mapping implementation backed by serialization and deserialization functions.
     *
     * @param <C> the ConnId value type
     * @param <P> the protocol (wire) value type
     */
    private record ValueMappingImpl<C,P>(Class<C> connIdType, Class<P> protocolType, Function<P,C> deserialize,
                                          Function<C, P> serialize) implements ValueMapping<C,P> {

        /**
         * Returns the ConnId value class for this mapping.
         *
         * @return the ConnId type class
         */
        @Override
        public Class<C> connIdType() {
            return connIdType;
        }

        /**
         * Returns the protocol (wire) value class for this mapping.
         *
         * @return the protocol type class
         */
        @Override
        public Class<? extends P> primaryWireType() {
            return protocolType;
        }

        /**
         * Converts a ConnId value to the protocol (wire) value using the configured serializer.
         *
         * @param value the ConnId-side value
         * @return the converted protocol-side value
         * @throws IllegalArgumentException if conversion fails
         */
        @Override
        public P toWireValue(C value) throws IllegalArgumentException {

            return serialize.apply(value);
        }

        /**
         * Converts a protocol (wire) value to the ConnId value using the configured deserializer.
         *
         * @param value the protocol-side value
         * @return the converted ConnId-side value
         * @throws IllegalArgumentException if conversion fails
         */
        @Override
        public C toConnIdValue(P value) throws IllegalArgumentException {
            return deserialize.apply(value);
        }
    }
}
