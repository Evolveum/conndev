/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.schema;

import com.evolveum.polygon.conndev.build.api.ValueMappingBuilder;
import com.evolveum.polygon.conndev.concepts.GroovyClosures;
import com.evolveum.polygon.conndev.spi.ValueMapping;
import groovy.lang.Closure;

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
public class BaseValueMappingBuilder<C,P> implements ValueMappingBuilder<C,P> {

    /** Function used to deserialize values from protocol type to ConnId type. */
    private Function<P, C> deserialize;

    /** Function used to serialize values from ConnId type to protocol type. */
    private Function<C, P> serialize;

    /** The ConnId value class for this mapping. */
    private final Class<C> connIdType;

    /** The protocol (wire) value class for this mapping. */
    private final Class<P> protocolType;

    /**
     * Constructs a new value mapping builder with the given ConnId and protocol types.
     *
     * @param connIdType the ConnId value class
     * @param protocolType the protocol (wire) value class
     */
    public BaseValueMappingBuilder(Class<C> connIdType, Class<P> protocolType) {
        this.connIdType = connIdType;
        this.protocolType = protocolType;
    }

    /**
     * Sets the deserialization function as a Groovy closure.
     * <p>
     * The closure receives the protocol-side value and returns the ConnId-side value.
     * Each invocation receives a fresh {@link DeserializationContext} as the closure's
     * delegate, providing access to the original value and metadata.
     *
     * @param closure the deserialization closure returning a ConnId value
     * @return this builder for chaining
     */
    @Override
    public ValueMappingBuilder<C,P> deserialize(Closure<C> closure) {
        this.deserialize = new DeserializeFunction<>(closure);
        return this;
    }

    /**
     * Sets the serialization function as a Groovy closure.
     * <p>
     * The closure receives the ConnId-side value and returns the protocol-side value.
     * The closure is automatically adapted from a groovy.lang.Closure to a
     * {@link java.util.function.Function}.
     *
     * @param closure the serialization closure returning a protocol value
     * @return this builder for chaining
     */
    @Override
    public ValueMappingBuilder<C,P> serialize(Closure<P> closure) {
        this.serialize = GroovyClosures.asFunction(closure);
        return this;
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

    /**
     * Adapts a Groovy closure to a {@link java.util.function.Function} for deserialization.
     * <p>
     * Each invocation wraps the input value in a {@link DeserializationContext} and
     * calls the closure with that context as the delegate, enabling rich DSL-style
     * deserialization logic.
     *
     * @param <C> the ConnId value type (closure return type)
     * @param <P> the protocol (wire) value type (closure input)
     */
    private record DeserializeFunction<C,P>(Closure<C> closure) implements Function<P, C> {

        /**
         * Applies the deserialization closure to the given protocol value.
         *
         * @param p the protocol-side value
         * @return the converted ConnId-side value
         */
        @Override
        public C apply(P p) {
            var context = new DeserializationContext<>(p);

            return (C) GroovyClosures.copyAndCall(closure, context);
        }
    }
}
