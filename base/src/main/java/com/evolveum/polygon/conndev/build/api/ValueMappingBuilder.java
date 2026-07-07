/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.build.api;

import com.evolveum.polygon.conndev.groovy.api.HelperFunctionsMixin;
import groovy.lang.Closure;
import groovy.lang.DelegatesTo;

/**
 * Builder for creating bidirectional value mappings between ConnId and protocol types.
 *
 * <p>Value mappings define how to serialize ConnId values to protocol wire format
 * and deserialize them back. Groovy closures are supported for flexible transformations.</p>
 *
 * @param <C> ConnId (Java) value type
 * @param <P> Protocol (wire) value type (typically JsonNode for REST/SCIM)
 */
public interface ValueMappingBuilder<C,P> {

    /**
     * Defines the deserialization closure (protocol value to ConnId value).
     *
     * @param closure a closure that transforms the protocol value into a ConnId value
     * @return this builder for chaining
     */
    ValueMappingBuilder<C,P> deserialize(@DelegatesTo(value = DeserializationContext.class, strategy = Closure.DELEGATE_ONLY) Closure<C> closure);

    /**
     * Defines the serialization closure (ConnId value to protocol value).
     *
     * @param closure a closure that transforms the ConnId value into a protocol value
     * @return this builder for chaining
     */
    ValueMappingBuilder<C,P> serialize(Closure<P> closure);

    /**
     * Provides access to the protocol value within a deserialization closure.
     *
     * <p>This record implements {@link HelperFunctionsMixin} to expose Groovy helper functions
     * (such as {@code getJsonString()}, {@code getJsonInt()}, etc.) within the closure body.</p>
     *
     * @param value the protocol value to deserialize
     *
     * @param <P> the protocol value type
     */
    record DeserializationContext<P>(P value) implements HelperFunctionsMixin {

        /**
         * Returns the protocol value being deserialized.
         *
         * @return the protocol value
         */
        public P getValue() {
            return value;
        }

    }
}
