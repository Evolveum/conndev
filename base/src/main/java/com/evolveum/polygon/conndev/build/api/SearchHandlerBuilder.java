package com.evolveum.polygon.conndev.build.api;

import com.evolveum.polygon.conndev.api.FilterSpecification;
import groovy.lang.Closure;
import groovy.lang.DelegatesTo;

/**
 * Builder for configuring search handler capabilities.
 *
 * <p>Search handlers define which filter types are supported by a search endpoint
 * and whether empty filter (unrestricted) searches are allowed. This is used for
 * custom search scripts that implement their own filter parsing.</p>
 *
 * @param <R> The concrete builder type (self-type for CRTP)
 */
public interface SearchHandlerBuilder<R extends SearchHandlerBuilder<R>> {

    /**
     * Sets whether the search endpoint supports filtering with empty filter criteria.
     *
     * <p>Only one such endpoint / custom script may be defined for the whole search handler.</p>
     *
     * @param emptyFilterSupported true if the endpoint should be used for searches without filters
     * @return this builder for chaining
     */
    R emptyFilterSupported(boolean emptyFilterSupported);

    /**
     * Configures an attribute specifier for use in filter definitions.
     *
     * @param name the attribute name
     * @return an attribute specification
     */
    FilterSpecification.Attribute attribute(String name);

    /**
     * Registers a filter specification as supported by this handler.
     *
     * @param filterSpec the filter specification to mark as supported
     * @return this builder for chaining
     */
    R supportedFilter(FilterSpecification filterSpec);

    /**
     * Registers a filter specification with a closure for additional configuration.
     *
     * @param filterSpec the filter specification to mark as supported
     * @param closure a closure for configuring the filter's capabilities
     * @return this builder for chaining
     */
    R supportedFilter(FilterSpecification filterSpec, @DelegatesTo(value = FilterSupportImplementation.class, strategy = Closure.DELEGATE_ONLY) Closure<?> closure);

    /**
     * A marker interface for filter support closures.
     *
     * <p>Closures passed to {@link SearchHandlerBuilder#supportedFilter(FilterSpecification, Closure)}
     * are delegated to an instance implementing this interface. Currently unused but reserved
     * for future extension.</p>
     */
    interface FilterSupportImplementation {

    }


}
