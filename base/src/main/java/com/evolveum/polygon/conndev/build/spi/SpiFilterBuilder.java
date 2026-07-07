/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.build.spi;

import com.evolveum.polygon.conndev.concepts.DefinitionValue;
import com.evolveum.polygon.conndev.concepts.Fluent;
import org.identityconnectors.framework.common.objects.filter.Filter;

/**
 * SPI-level filter builder base interface.
 *
 * <p>This interface is the service-provider counterpart to
 * {@link com.evolveum.polygon.conndev.build.api.FilterBuilder}.
 * SPI implementations use DefinitionValue to carry provenance metadata
 * for filter expressions.</p>
 */
public interface SpiFilterBuilder extends Fluent<SpiFilterBuilder> {

    /**
     * Registers an equality condition on an attribute.
     *
     * @param attributeName the attribute name with metadata
     * @param value the value to compare against with metadata
     * @return this builder for chaining
     */
    SpiFilterBuilder eq(DefinitionValue<String> attributeName, DefinitionValue<Object> value);

    /**
     * Creates an attribute filter with the given name.
     *
     * @param name the attribute name definition
     * @return a new attribute filter builder
     */
    SpiAttributeFilterBuilder attribute(DefinitionValue<String> name);

    /**
     * SPI attribute-level filter builder.
     *
     * @param name the attribute name
     */
    record SpiAttributeFilterBuilder(String name) {

        /**
         * Registers an equality condition on this attribute.
         *
         * @param attributeName the attribute name with metadata (ignored, uses record name)
         * @param value the value to compare against with metadata
         * @return this builder for chaining
         */
        public SpiAttributeFilterBuilder eq(DefinitionValue<String> attributeName, DefinitionValue<Object> value) {
            return this;
        }

        /**
         * Creates an attribute filter with the given name.
         *
         * @param name the attribute name definition
         * @return a new attribute filter builder
         */
        public SpiAttributeFilterBuilder attribute(DefinitionValue<String> name) {
            return new SpiAttributeFilterBuilder(name.value());
        }

        /**
         * Creates an equality condition.
         *
         * @param value the value to compare against
         * @return this builder for chaining
         */
        public SpiAttributeFilterBuilder eq(Object value) {
            return this;
        }
    }

    /**
     * Returns the built filter.
     *
     * @return the filter instance
     */
    Filter build();
}