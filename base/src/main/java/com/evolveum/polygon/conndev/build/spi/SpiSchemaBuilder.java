/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.build.spi;

import com.evolveum.polygon.conndev.build.api.ObjectClassSchemaBuilder;
import com.evolveum.polygon.conndev.build.api.ReferenceAttributeBuilder;
import com.evolveum.polygon.conndev.build.api.RelationshipBuilder;
import com.evolveum.polygon.conndev.concepts.DefinitionValue;
import com.evolveum.polygon.conndev.concepts.Fluent;
import com.evolveum.polygon.conndev.concepts.GroovyClosures;
import groovy.lang.Closure;
import groovy.lang.DelegatesTo;

import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * SPI-level schema builder base interface.
 *
 * <p>This interface is the service-provider counterpart to
 * {@link com.evolveum.polygon.conndev.build.api.SchemaBuilder}.
 * SPI implementations use DefinitionValue to carry provenance metadata
 * for schema definitions.</p>
 *
 * @param <SB> The concrete schema builder type (self-type for CRTP)
 * @param <OB> The object class schema builder type
 */
public interface SpiSchemaBuilder<SB extends SpiSchemaBuilder<SB, OB>, OB extends ObjectClassSchemaBuilder<OB, ?,  ?>> extends Fluent<SB> {

    /**
     * Creates or gets an object class schema builder by name.
     *
     * @param name the object class name definition (used as the ConnId object class value)
     * @return the object class schema builder for further configuration
     */
    OB objectClass(DefinitionValue<String> name);

    /**
     * Creates or gets an object class schema builder by name, applying a closure to configure it.
     *
     * @param name the object class name
     * @param closure a closure that configures the {@link SpiObjectClassSchemaBuilder} instance
     * @return the configured object class schema builder
     */
    default OB objectClass(DefinitionValue<String> name,
                          @DelegatesTo(SpiObjectClassSchemaBuilder.class) Closure<?> closure) {
        OB builder = objectClass(name);
        GroovyClosures.callAndReturnDelegate(closure, builder);
        return builder;
    }

    /**
     * Searches for an existing object class builder matching the provided predicate.
     *
     * @param lookup a predicate used to find first matching object class builder
     * @return an optional containing the matching object class builder if found, otherwise an empty optional
     */
    Optional<OB> lookupObjectClass(Predicate<OB> lookup);

    /**
     * Correlates an existing object class based on the provided predicate, or creates a new one with the specified name and configuration.
     *
     * If an existing object class matching the lookup predicate is found, it is returned immediately. Otherwise, a new object class
     * builder is created using the provided name definition, the provided customizer function is applied to configure it, and the
     * resulting builder is returned.
     *
     * @param lookup a predicate used to search for an existing object class
     * @param newName the definition value containing the name for a newly created object class
     * @param newCustomizer a consumer function to configure a newly created object class builder
     * @return the correlated existing object class builder, or the newly created and configured object class builder
     */
    default OB correlateObjectClass(Predicate<OB> lookup, DefinitionValue<String> newName, Consumer<OB> newCustomizer) {
        var maybe = lookupObjectClass(lookup);
        if (maybe.isPresent()) {
            return maybe.get();
        }
        var builder = objectClass(newName);
        newCustomizer.accept(builder);
        return builder;
    }
}