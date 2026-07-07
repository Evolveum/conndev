/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.build.api;

import com.evolveum.polygon.conndev.annotations.Script;
import com.evolveum.polygon.conndev.concepts.Fluent;
import groovy.lang.Closure;
import groovy.lang.DelegatesTo;

/**
 *
 * @param <B> Final Object Class Builder interface
 * @param <A> Final Attribute Builder interface
 * @param <R> Final Reference Attribute Builder interface
 */
public interface ObjectClassSchemaBuilder<B extends ObjectClassSchemaBuilder<B, A, R> , A extends AttributeBuilder<? super R,?>, R extends ReferenceAttributeBuilder<R, A, ?>> extends Fluent<B> {

    /**
     * Sets a description for this object class definition.
     *
     * @param description the object class description
     * @return this builder for chaining
     */
    B description(String description);

    /**
     * Marks the object class as embedded.
     *
     * An embedded object class is returned inline rather than by reference,
     * typically used for simple data containers such as addresses or contact details.
     *
     * @param embedded true if the object class is embedded, false if it is referenced
     * @return this builder for chaining
     */
    B embedded(boolean embedded);

    /**
     * Creates / gets attribute definition with the specified name.
     *
     * @param name the name of the attribute to be configured
     * @return an instance of {@link AttributeBuilder} for further configuration of the attribute
     */
    A attribute(String name);

    /**
     * Creates / gets reference definition with the specified name.
     *
     * @param name the name of the reference attribute to be configured
     * @return an instance of {@link ReferenceAttributeBuilder} for further configuration of the reference attribute
     */
    R reference(String name);

    /**
     * Creates or gets an attribute definition with the specified name, applying a closure to further configure it.
     *
     * @param name the name of the attribute to be configured
     * @param closure a closure that configures the {@link AttributeBuilder} instance for the specified attribute
     * @return an instance of {@link AttributeBuilder} for further configuration of the attribute
     */
    A attribute(String name,
                 @Script.Initialization
                 @DelegatesTo(value = AttributeBuilder.class, strategy = Closure.DELEGATE_ONLY)
                 Closure<?> closure);

    /**
     * Creates / gets reference definition with the specified name, applying a closure to further configure it.
     *
     * @param name the name of the reference attribute to be configured
     * @param closure a closure that configures the {@link ReferenceAttributeBuilder} instance for the specified reference attribute
     * @return an instance of {@link ReferenceAttributeBuilder} for further configuration of the reference attribute
     */
    R reference(String name, @DelegatesTo(ReferenceAttributeBuilder.class) Closure<?> closure);

    /**
     * Maps a ConnId builtin attribute name (like "UID", "NAME") to a protocol attribute.
     * Supported built-in names: "UID", "NAME".
     *
     * @param connIdName the ConnId attribute name (must be "UID" or "NAME")
     * @param attributeName the protocol (JSON) attribute name to bind it to
     * @return this builder for chaining
     */
    B connIdAttribute(String connIdName, String attributeName);

    /**
     * Returns the self reference for method chaining.
     *
     * @return this instance
     */
    B self();

}
