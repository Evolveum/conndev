/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.build.spi;

import com.evolveum.polygon.conndev.concepts.DefinitionValue;
import com.evolveum.polygon.conndev.concepts.Fluent;
import groovy.lang.Closure;
import groovy.lang.DelegatesTo;

/**
 * SPI-level attribute resolver builder base interface.
 *
 * <p>This interface is the service-provider counterpart to
 * {@link com.evolveum.polygon.conndev.build.api.AttributeResolverBuilder}.
 * SPI implementations use DefinitionValue to carry provenance metadata
 * for resolver definitions.</p>
 *
 * <p>Attribute resolvers are used to resolve attributes from a data source into ConnId objects.</p>
 */
public interface SpiAttributeResolverBuilder extends Fluent<SpiAttributeResolverBuilder> {

    /**
     * Sets the resolution type for handling attributes.
     *
     * @param type the resolution type with metadata
     * @return This builder instance for method chaining.
     */
    SpiAttributeResolverBuilder resolutionType(DefinitionValue<ResolutionType> type);

    /**
     * Search-based resolver. Resolves reference values by performing search of target object class.
     *
     * @param filterClosure Closure which builds filter with metadata.
     * @return This builder instance for method chaining.
     */
    SpiAttributeResolverBuilder search(@DelegatesTo(value = SpiSearchScriptBuilder.class, strategy = Closure.DELEGATE_FIRST) Closure<?> filterClosure);

    /**
     * Groovy implementation of attribute resolution.
     *
     * @param implementationClosure The closure defining custom behavior for attribute resolution with metadata.
     * @return This builder instance for method chaining.
     */
    SpiAttributeResolverBuilder implementation(@DelegatesTo(value = SpiAttributeResolverBuilder.class, strategy = Closure.DELEGATE_ONLY) Closure<?> implementationClosure);

    /**
     * Marks attribute as supported for resolver.
     *
     * @param attributeName name of the attribute definition
     * @return this builder
     */
    SpiAttributeResolverBuilder attribute(DefinitionValue<String> attributeName);

    /**
     * The resolution type enumeration.
     */
    enum ResolutionType {
        /**
         * Each object will be handled individually, regardless of the total number of objects involved.
         */
        PER_OBJECT,
        /**
         * Objects will be handled in batches, use this if backing implementation supports handling multiple objects per request.
         */
        BATCH
    }
}