/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.build.spi;

import com.evolveum.polygon.conndev.concepts.DefinitionValue;
import com.evolveum.polygon.conndev.concepts.Fluent;

/**
 * SPI-level reference attribute builder base interface.
 *
 * <p>This interface is the service-provider counterpart to
 * {@link com.evolveum.polygon.conndev.build.api.ReferenceAttributeBuilder}.
 * SPI implementations use DefinitionValue to carry provenance metadata
 * for reference attribute definitions.</p>
 *
 * @param <B> The concrete builder type (self-type for CRTP)
 * @param <A> The parent attribute builder type
 * @param <P> The protocol type (e.g. JsonNode for REST/SCIM)
 */
public interface SpiReferenceAttributeBuilder<B extends SpiReferenceAttributeBuilder<B, A, P>, A extends SpiAttributeBuilder<A, P>, P> extends SpiAttributeBuilder<B, P> {

    /**
     * Specifies the target ConnId object class for the reference.
     *
     * @param objectClass the target object class definition
     * @return the current instance for method chaining
     */
    B objectClass(DefinitionValue<String> objectClass);

    /**
     * Specifies a subtype qualifier for the reference attribute.
     *
     * @param subtype the subtype definition
     * @return the current instance for method chaining
     */
    B subtype(DefinitionValue<String> subtype);

    /**
     * Specifies the role for the reference by name.
     *
     * @param role the role name definition
     * @return the current instance for method chaining
     */
    B role(DefinitionValue<String> role);

    /**
     * A proxy interface that delegates all reference attribute builder methods to an underlying instance.
     *
     * Useful for building reference attributes in a deferred or wrapped manner.
     */
    interface Delegator<B extends SpiReferenceAttributeBuilder<B, A, P>, A extends SpiAttributeBuilder<A, P>, P> extends SpiReferenceAttributeBuilder<B, A, P> {
        /**
         * Returns the underlying delegate reference builder.
         *
         * @return the delegate instance
         */
        B delegate();
    }
}