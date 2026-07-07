/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.build.spi;

import com.evolveum.polygon.conndev.api.FilterSpecification;
import com.evolveum.polygon.conndev.concepts.DefinitionValue;
import com.evolveum.polygon.conndev.concepts.Fluent;
import groovy.lang.Closure;
import groovy.lang.DelegatesTo;

/**
 * SPI-level search script builder base interface.
 *
 * <p>This interface is the service-provider counterpart to
 * {@link com.evolveum.polygon.conndev.build.api.SearchScriptBuilder}.
 * SPI implementations use DefinitionValue to carry provenance metadata
 * for script definitions.</p>
 */
public interface SpiSearchScriptBuilder extends Fluent<SpiSearchScriptBuilder> {

    /**
     * Specifies whether searches without filter criteria are supported.
     *
     * @param emptyFilterSupported the unsupported flag with metadata
     * @return this builder for chaining
     */
    SpiSearchScriptBuilder emptyFilterSupported(DefinitionValue<Boolean> emptyFilterSupported);

    /**
     * Supplies the custom search implementation as a Groovy closure.
     *
     * @param implementation a closure that executes the custom search logic with metadata
     * @return this builder for chaining
     */
    SpiSearchScriptBuilder implementation(@DelegatesTo(value = SpiSearchScriptBuilder.class, strategy = Closure.DELEGATE_ONLY) Closure<?> implementation);

    /**
     * Registers a filter specification that this search supports.
     *
     * @param filterSpec the filter specification with metadata
     * @return this builder for chaining
     */
    SpiSearchScriptBuilder supportedFilter(FilterSpecification filterSpec);

    /**
     * Convenience factory for creating a filter attribute specification.
     *
     * @param name the attribute name for the filter specification
     * @return a new attribute specification for use in {@code supportedFilter}
     */
    FilterSpecification.Attribute attribute(DefinitionValue<String> name);
}