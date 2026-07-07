/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.build.spi;

import com.evolveum.polygon.conndev.concepts.DefinitionValue;
import com.evolveum.polygon.conndev.concepts.Fluent;
import com.evolveum.polygon.conndev.spi.ValueMapping;
import com.fasterxml.jackson.databind.JsonNode;
import groovy.lang.Closure;
import groovy.lang.DelegatesTo;

/**
 * SPI-level value mapping builder base interface.
 *
 * <p>This interface is the service-provider counterpart to
 * {@link com.evolveum.polygon.conndev.build.api.ValueMappingBuilder}.
 * SPI implementations use DefinitionValue to carry provenance metadata for
 * mapping declarations.</p>
 *
 * @param <C> The ConnId (Java) value type
 * @param <P> The Protocol (wire) value type
 */
public interface SpiValueMappingBuilder<C, P> extends Fluent<SpiValueMappingBuilder<C, P>> {

    /**
     * Sets the deserialization implementation via a DefinitionValue.
     *
     * @param mapping the deserialization value mapping with metadata
     * @return this builder for chaining
     */
    SpiValueMappingBuilder<C, P> deserialization(DefinitionValue<ValueMapping<C, P>> mapping);

    /**
     * Sets the serialization implementation via a DefinitionValue.
     *
     * @param mapping the serialization value mapping with metadata
     * @return this builder for chaining
     */
    SpiValueMappingBuilder<C, P> serialization(DefinitionValue<ValueMapping<P, C>> mapping);

    /**
     * Sets the deserialization closure.
     *
     * @param closure a closure that transforms the protocol value into a ConnId value
     * @return this builder for chaining
     */
    SpiValueMappingBuilder<C, P> deserialize(@DelegatesTo(value = DeserializationContext.class, strategy = Closure.DELEGATE_ONLY) Closure<C> closure);

    /**
     * Sets the serialization closure.
     *
     * @param closure a closure that transforms the ConnId value into a protocol value
     * @return this builder for chaining
     */
    SpiValueMappingBuilder<C, P> serialize(Closure<P> closure);

    /**
     * SPI-level deserialization context record.
     *
     * @param <P> the protocol value type
     */
    record DeserializationContext<P>(P value) {

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