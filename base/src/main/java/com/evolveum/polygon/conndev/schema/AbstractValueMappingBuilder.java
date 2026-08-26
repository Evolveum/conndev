/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.schema;

import com.evolveum.polygon.conndev.build.api.ValueMappingBuilder;
import com.evolveum.polygon.conndev.concepts.GroovyClosures;
import groovy.lang.Closure;

import java.util.function.Function;

/**
 * Base class for fluent builders that configure conversions between ConnId
 * values and protocol-specific values.
 *
 * <p>Conversion functions, deserialization (protocol-to-ConnId) and serialization
 *  * (ConnId-to-protocol), are supplied as Groovy closures. During each
 * conversion, the closure is invoked with a fresh
 * {@link DeserializationContext} or {@link SerializationContext} as its
 * delegate.
 *
 * @param <C> the ConnId-side value type
 * @param <P> the protocol-side value type
 * @param <B> the concrete builder type
 */

public abstract class AbstractValueMappingBuilder<C, P, B extends AbstractValueMappingBuilder<C, P, B>>
        implements ValueMappingBuilder<C, P> {

    /** Function used to deserialize values from protocol type to ConnId type. */
    protected Function<P, C> deserialize;

    /** Function used to serialize values from ConnId type to protocol type. */
    protected Function<C, P> serialize;

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
    @SuppressWarnings("unchecked")
    public B deserialize(Closure<C> closure) {
        this.deserialize = new DeserializeFunction<>(closure);
        return (B) this;
    }

    /**
     * Sets the serialization function as a Groovy closure.
     * <p>
     * The closure receives the ConnId-side value and returns the protocol-side value.
     * Each invocation receives a fresh {@link SerializationContext} as the closure's
     * delegate, providing access to the original value and metadata.
     *
     * @param closure the serialization closure returning a protocol value
     * @return this builder for chaining
     */

    @Override
    @SuppressWarnings("unchecked")
    public B serialize(Closure<P> closure) {
        this.serialize = new SerializeFunction<>(closure);
        return (B) this;
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
    private record DeserializeFunction<C, P>(Closure<C> closure) implements Function<P, C> {

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

    /**
     * Adapts a Groovy closure to a {@link java.util.function.Function} for serialization.
     * <p>
     * Each invocation wraps the input ConnId value in a {@link SerializationContext} and
     * calls the closure with that context as the delegate, enabling rich DSL-style
     * serialization logic.
     *
     * @param <C> the ConnId value type (closure input)
     * @param <P> the protocol (wire) value type (closure return type)
     */
    private record SerializeFunction<C, P>(Closure<P> closure) implements Function<C, P> {

        /**
         * Applies the serialization closure to the given connId value.
         *
         * @param c the ConnId-side value
         * @return the converted protocol-side value
         */
        @Override
        public P apply(C c) {
            var context = new SerializationContext<>(c);
            return (P) GroovyClosures.copyAndCall(closure, context);
        }
    }
}