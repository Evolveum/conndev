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
 * SPI-level object class schema builder base interface.
 *
 * <p>This interface is the service-provider counterpart to
 * {@link com.evolveum.polygon.conndev.build.api.ObjectClassSchemaBuilder}.
 * SPI implementations use DefinitionValue to carry provenance metadata
 * for object class definitions.</p>
 *
 * @param <B> Final Object Class Builder interface
 * @param <A> Final Attribute Builder interface
 * @param <R> Final Reference Attribute Builder interface
 * @param <P> Protocol type (e.g. JsonNode)
 */
public interface SpiObjectClassSchemaBuilder<B extends SpiObjectClassSchemaBuilder<B, A, R, P>, A extends SpiAttributeBuilder<A, P>, R extends SpiReferenceAttributeBuilder<R, A, P>, P> extends Fluent<B> {

    /**
     * Sets a description for this object class definition.
     *
     * @param description the object class description with metadata
     * @return this builder for chaining
     */
    B description(DefinitionValue<String> description);

    /**
     * Marks the object class as embedded.
     *
     * An embedded object class is returned inline rather than by reference,
     * typically used for simple data containers such as addresses or contact details.
     *
     * @param embedded the embedded flag with metadata
     * @return this builder for chaining
     */
    B embedded(DefinitionValue<Boolean> embedded);

    /**
     * Creates / gets attribute definition with the specified name.
     *
     * @param name the name of the attribute to be configured with metadata
     * @return an instance of {@link SpiAttributeBuilder} for further configuration
     */
    A attribute(DefinitionValue<String> name);

    /**
     * Creates / gets reference definition with the specified name.
     *
     * @param name the name of the reference attribute to be configured with metadata
     * @return an instance of {@link SpiReferenceAttributeBuilder} for further configuration
     */
    R reference(DefinitionValue<String> name);

    /**
     * Maps a ConnId builtin attribute name (like "UID", "NAME") to a protocol attribute.
     *
     * @param connIdName the ConnId attribute name (must be "UID" or "NAME") with metadata
     * @param attributeName the protocol (JSON) attribute name to bind it with metadata
     * @return this builder for chaining
     */
    B connIdAttribute(DefinitionValue<String> connIdName, DefinitionValue<String> attributeName);

    /**
     * Returns the self reference for method chaining.
     *
     * @return this instance
     */
    @Override
    B self();
}