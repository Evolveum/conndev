/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.build.api;

import com.evolveum.polygon.conndev.annotations.Script;
import com.evolveum.polygon.conndev.api.FilterSpecification;
import com.evolveum.polygon.conndev.groovy.api.SearchScriptContext;
import groovy.lang.Closure;
import groovy.lang.DelegatesTo;


/**
 * Builder for custom search scripts.
 *
 * <p>Enables defining a complete custom search operation via a Groovy closure,
 * including script implementation, supported filter types, and whether empty
 * filter searches are supported (for full-object retrieval).</p>
 *
 * @see SearchScriptContext
 */
public interface SearchScriptBuilder {

    /**
     * Specifies whether searches without filter criteria are supported.
     *
     * An empty filter search typically retrieves all objects of an object class.
     *
     * @param emptyFilterSupported true if unfiltered searches are allowed
     * @return this builder for chaining
     */
    SearchScriptBuilder emptyFilterSupported(boolean emptyFilterSupported);

    /**
     * Supplies the custom search implementation as a Groovy closure.
     *
     *
     * @param implementation a closure that executes the custom search logic
     * @return this builder for chaining
     */
    SearchScriptBuilder implementation(@DelegatesTo(SearchScriptContext.class) @Script.Runtime Closure<?> implementation);

    /**
     * Registers a filter specification that this search supports.
     *
     * Filters registered via this method are included in the handler's
     * supported filter list reported to the connector framework.
     *
     * @param filterSpec the filter specification to mark as supported
     * @return this builder for chaining
     */
    SearchScriptBuilder supportedFilter(FilterSpecification filterSpec);

    /**
     * Convenience factory for creating a filter attribute specification.
     *
     * @param name the attribute name for the filter specification
     * @return a new attribute specification for use in {@code supportedFilter}
     */
    FilterSpecification.Attribute attribute(String name);


}
