/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.build.api;

import org.identityconnectors.framework.common.objects.AttributeBuilder;
import org.identityconnectors.framework.common.objects.ConnectorObjectBuilder;
import org.identityconnectors.framework.common.objects.ConnectorObjectReference;
import org.identityconnectors.framework.common.objects.filter.EqualsFilter;
import org.identityconnectors.framework.common.objects.filter.Filter;

/**
 * Utility interface for building ConnId {@link org.identityconnectors.framework.common.objects.filter.Filter} instances.
 *
 * <p>Provides a factory method for creating attribute-equals filters in a type-safe manner,
 * automatically converting {@link org.identityconnectors.framework.common.objects.ConnectorObjectBuilder}
 * instances to {@link org.identityconnectors.framework.common.objects.ConnectorObjectReference}s.</p>
 */
public interface FilterBuilder {

    /**
     * Creates a new filter builder for the given attribute name.
     *
     * @param name the attribute name to filter on
     * @return a new {@link AttributeFilterBuilder} instance
     */
    static AttributeFilterBuilder forAttribute(String name) {
        return new AttributeFilterBuilder(name);
    }

    /**
     * A record-based filter builder convenience for equality filters on a named attribute.
     *
     * <p>Provides an {@code eq()} method that automatically handles {@link ConnectorObjectBuilder}
     * conversion to references for link comparison.</p>
     *
     * @param name the attribute name to filter on
     */
    record AttributeFilterBuilder(String name) implements FilterBuilder {

        /**
         * Creates an equality filter for the given value.
         *
         * <p>If the value is a {@link ConnectorObjectBuilder}, it is automatically
         * converted to a {@link ConnectorObjectReference} for proper link comparison.</p>
         *
         * @param value the value to compare against
         * @return an equals filter
         */
        public EqualsFilter eq(Object value) {
            if (value instanceof ConnectorObjectBuilder builder) {
                value = new ConnectorObjectReference(builder.build());
            }
            return new EqualsFilter(AttributeBuilder.build(name, value));
        }

        /**
         * Builds the filter (not yet implemented).
         *
         * @return the built filter
         */
        @Override
        public Filter build() {
            return null;
        }
    }

    /**
     * Builds the {@link Filter} instance.
     *
     * @return the built filter
     */
    Filter build();
}
