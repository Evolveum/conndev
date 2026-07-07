/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.build.api;

import com.evolveum.polygon.conndev.concepts.GroovyClosures;
import com.evolveum.polygon.conndev.spi.ObjectSearchOperation;
import groovy.lang.Closure;
import groovy.lang.DelegatesTo;

/**
 * Builder for configuring the search operation on an object class.
 *
 * <p>Search operation configuration includes attribute resolvers for resolving reference
 * attributes at search time, normalization rules for transforming fetched data,
 * and optional custom search scripts.</p>
 *
 * @see SearchOperationBuilder#attributeResolver()
 * @see SearchOperationBuilder#normalize()
 * @see SearchOperationBuilder#custom()
 */
public interface SearchOperationBuilder extends ObjectClassOperationBuilder<ObjectSearchOperation> {

    /**
     * Returns the attribute resolver builder for configuring reference attribute resolution.
     *
     * @return the attribute resolver builder
     */
    AttributeResolverBuilder attributeResolver();

    /**
     * Configures the attribute resolver builder via a closure and returns it.
     *
     * @param definition a closure for configuring the {@link AttributeResolverBuilder}
     * @return the configured attribute resolver builder
     */
    default AttributeResolverBuilder attributeResolver(
            @DelegatesTo(value = AttributeResolverBuilder.class, strategy = Closure.DELEGATE_ONLY) Closure<?> definition) {
        return GroovyClosures.callAndReturnDelegate(definition, attributeResolver());
    }

    /**
     * Returns the normalization builder for fetched-data normalization rules.
     *
     * @return the normalization builder
     */
    NormalizationBuilder normalize();

    /**
     * Configures the normalization builder via a closure.
     *
     * @param definition a closure that configures the {@link NormalizationBuilder}
     * @return the configured normalization builder
     */
    default NormalizationBuilder normalize(@DelegatesTo(value = NormalizationBuilder.class, strategy = Closure.DELEGATE_ONLY) Closure<?> definition) {
        return GroovyClosures.callAndReturnDelegate(definition, normalize());
    }

    /**
     * Returns the builder for a custom search script.
     *
     * @return the search script builder
     */
    SearchScriptBuilder custom();

    /**
     * Configures the search script builder via a closure.
     *
     * @param definition a closure that configures the {@link SearchScriptBuilder}
     * @return the configured search script builder
     */
    default SearchScriptBuilder custom(Closure<?> definition) {
        return GroovyClosures.callAndReturnDelegate(definition, custom());
    }

}
