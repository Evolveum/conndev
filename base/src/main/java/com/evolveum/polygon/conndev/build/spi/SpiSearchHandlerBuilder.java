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
 * SPI-level search handler builder base interface.
 *
 * <p>This interface is the service-provider counterpart to
 * {@link com.evolveum.polygon.conndev.build.api.SearchHandlerBuilder}.
 * SPI implementations use DefinitionValue to carry provenance metadata
 * for handler definitions.</p>
 *
 * @param <R> The concrete builder type (self-type for CRTP)
 */
public interface SpiSearchHandlerBuilder<R extends SpiSearchHandlerBuilder<R>> extends Fluent<R> {

    /**
     * Sets whether the search endpoint supports filtering with empty filter criteria.
     *
     * @param emptyFilterSupported the flag with metadata
     * @return this builder for chaining
     */
    R emptyFilterSupported(DefinitionValue<Boolean> emptyFilterSupported);

    /**
     * Configures an attribute specifier for use in filter definitions.
     *
     * @param name the attribute name with metadata
     * @return an attribute specification
     */
    FilterSpecification.Attribute attribute(DefinitionValue<String> name);

    /**
     * Registers a filter specification as supported by this handler.
     *
     * @param filterSpec the filter specification to mark as supported with metadata
     * @return this builder for chaining
     */
    R supportedFilter(FilterSpecification filterSpec);

    /**
     * Registers a filter specification with a closure for additional configuration.
     *
     * @param filterSpec the filter specification to mark as supported with metadata
     * @param closure a closure for configuring the filter's capabilities
     * @return this builder for chaining
     */
    R supportedFilter(FilterSpecification filterSpec, @DelegatesTo(value = FilterSupportImplementation.class, strategy = Closure.DELEGATE_ONLY) Closure<?> closure);

    /**
     * A marker interface for filter support closures.
     */
    interface FilterSupportImplementation {
    }
}