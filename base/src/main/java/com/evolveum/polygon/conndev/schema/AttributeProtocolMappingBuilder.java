/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.schema;

import com.evolveum.polygon.conndev.spi.AttributeProtocolMapping;

/**
 * Builder for creating {@link AttributeProtocolMapping} instances.
 * Implementations configure how an attribute is mapped between ConnId and a
 * protocol-specific representation (e.g., JSON), then produce the immutable
 * mapping via {@link #build()}.
 */
public interface AttributeProtocolMappingBuilder {

    /**
     * Returns the ConnId type that should be used for this attribute mapping,
     * or {@code null} if the default type (derived from the protocol mapping's
     * implementation) should be used. When non-{@code null}, the returned type
     * overrides the type declared by the built {@link AttributeProtocolMapping}.
     *
     * @return the desired ConnId type, or {@code null} for default behavior
     */
    Class<?> suggestedConnIdType();

    /**
     * Builds and returns an immutable {@link AttributeProtocolMapping}
     * configured according to the builder's settings.
     *
     * @return a new attribute protocol mapping instance
     */
    AttributeProtocolMapping<?,?> build();

}
