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
 * SPI-level normalization builder base interface.
 *
 * <p>This interface is the service-provider counterpart to
 * {@link com.evolveum.polygon.conndev.build.api.NormalizationBuilder}.
 * SPI implementations use DefinitionValue to carry provenance metadata
 * for normalization rules.</p>
 */
public interface SpiNormalizationBuilder extends Fluent<SpiNormalizationBuilder> {

    /**
     * Configures normalization to split a single-value attribute into multiple values.
     *
     * @param attribute the attribute definition to split into multiple values
     * @return this normalizer for chaining
     */
    SpiNormalizationBuilder toSingleValue(DefinitionValue<String> attribute);

    /**
     * Configures a closure to rewrite UIDs based on normalized values.
     *
     * @param implementation a closure that computes new UID values from normalized value definition
     * @return this normalizer for chaining
     */
    SpiNormalizationBuilder rewriteUid(@DelegatesTo(value = SpiNormalizationBuilder.class, strategy = Closure.DELEGATE_ONLY) Closure<?> implementation);

    /**
     * Configures a closure to rewrite names based on normalized values.
     *
     * @param implementation a closure that computes new name values from normalized value definition
     * @return this normalizer for chaining
     */
    SpiNormalizationBuilder rewriteName(@DelegatesTo(value = SpiNormalizationBuilder.class, strategy = Closure.DELEGATE_ONLY) Closure<?> implementation);

    /**
     * Configures a closure to restore UIDs to their original values before normalization.
     *
     * @param implementation a closure that restores UID values from definition value
     * @return this normalizer for chaining
     */
    SpiNormalizationBuilder restoreUid(@DelegatesTo(value = SpiNormalizationBuilder.class, strategy = Closure.DELEGATE_ONLY) Closure<?> implementation);

    /**
     * Configures a closure to restore names to their original values before normalization.
     *
     * @param implementation a closure that restores name values from definition value
     * @return this normalizer for chaining
     */
    SpiNormalizationBuilder restoreName(@DelegatesTo(value = SpiNormalizationBuilder.class, strategy = Closure.DELEGATE_ONLY) Closure<?> implementation);
}