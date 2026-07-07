/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.build.spi;

import com.evolveum.polygon.conndev.build.api.AttributeBuilder;
import com.evolveum.polygon.conndev.concepts.DefinitionValue;
import com.evolveum.polygon.conndev.concepts.Fluent;
import com.evolveum.polygon.conndev.concepts.FluentBuilder;

/**
 * SPI-level attribute builder base interface.
 *
 * <p>This interface is the service-provider counterpart to {@link com.evolveum.polygon.conndev.build.api.AttributeBuilder}.
 * Unlike the API interface which takes plain values, this SPI interface uses
 * {@link com.evolveum.polygon.conndev.concepts.DefinitionValue DefinitionValue} to carry
 * value origin (DEFAULT, DETECTED, DECLARED) and source location information for
 * better error reporting. Automated detection algorithms should use this interface to populate
 * values with correct origin. </p>
 *
 * <p>Implementations typically live in the {@code schema/} package (e.g. {@code AbstractAttributeBuilder})
 * and bridge the gap between the DSL-level API builder and runtime metadata.</p>
 *
 * @param <B> The concrete builder type, bounded by the public API {@code AttributeBuilder}
 * @param <P> The protocol type (e.g. JsonNode)
 *
 * @see com.evolveum.polygon.conndev.build.api.AttributeBuilder
 * @see com.evolveum.polygon.conndev.schema.AbstractAttributeBuilder
 */
public interface SpiAttributeBuilder<B extends AttributeBuilder<B,P>, P> extends FluentBuilder<B, P> {

    /**
     * Marks the attribute as emulated.
     *
     * @param emulated the emulated flag with metadata
     * @return this builder for chaining
     */
    B emulated(DefinitionValue<Boolean> emulated);

    /**
     * Sets the protocol name (the name as it appears on the wire, e.g. JSON key).
     *
     * @param protocolName the protocol name with metadata
     * @return this builder for chaining
     */
    B protocolName(DefinitionValue<String> protocolName);

    /**
     * Sets the remote name (may differ from the protocol name).
     *
     * @param remoteName the remote name with metadata
     * @return this builder for chaining
     */
    B remoteName(DefinitionValue<String> remoteName);

    /**
     * Associates this attribute with an object class (complex type).
     *
     * When set, the attribute type is automatically changed to
     * {@link org.identityconnectors.framework.common.objects.ConnectorObjectReference}
     * and the JSON mapping is set to an {@link com.evolveum.polygon.conndev.spi.EmbeddedObjectJsonMapping}.
     *
     * @param objectClass the referenced object class name with metadata, or {@code null} to clear
     * @return this builder for chaining
     */
    B complexType(DefinitionValue<String> objectClass);

    /**
     * Builds the final attribute definition.
     *
     * @throws UnsupportedOperationException always — must be overridden by implementations
     * @return never returns
     */
    @Override
    default P build() {
        throw new UnsupportedOperationException("Implementation required");
    }

    /**
     * ConnId-side attribute metadata configuration.
     *
     * <p>Configures the ConnId {@link org.identityconnectors.framework.common.objects.AttributeInfo}
     * properties: name, type, multiplicity, and operational flags (readable, required,
     * creatable, updatable). Uses {@link DefinitionValue} to track value sources.</p>
     *
     * @param <F> The concrete mapping type (self-type for CRTP)
     */
    interface ConnIdMapping<F extends ConnIdMapping<F>> extends Fluent<F> {

        /**
         * Sets the ConnId attribute name.
         *
         * @param name the attribute name with metadata
         * @return this mapping instance for chaining
         */
        F name(DefinitionValue<String> name);

        /**
         * Sets the native attribute name (usually the same as the ConnId name).
         *
         * @param name the native name with metadata
         * @return this mapping instance for chaining
         */
        F nativeName(DefinitionValue<String> name);

        /**
         * Sets the Java type of the attribute.
         *
         * @param connIdType the Java class with metadata
         * @return this mapping instance for chaining
         */
        F type(DefinitionValue<Class<?>> connIdType);

        /**
         * Specifies whether the attribute is readable (can be fetched).
         *
         * @param readable the readable flag with metadata
         * @return this mapping instance for chaining
         */
        F readable(DefinitionValue<Boolean> readable);

        /**
         * Specifies whether the attribute is required (must be provided in create/update).
         *
         * @param required the required flag with metadata
         * @return this mapping instance for chaining
         */
        F required(DefinitionValue<Boolean> required);

        /**
         * Sets the human-readable description of the attribute.
         *
         * @param description the description with metadata
         * @return this mapping instance for chaining
         */
        F description(DefinitionValue<String> description);

        /**
         * Specifies whether the attribute is returned by default.
         *
         * @param returnedByDefault the flag with metadata
         * @return this mapping instance for chaining
         */
        F returnedByDefault(DefinitionValue<Boolean> returnedByDefault);

        /**
         * Specifies whether the attribute is multi-valued.
         *
         * @param multiValued the multiplicity flag with metadata
         * @return this mapping instance for chaining
         */
        F multiValued(DefinitionValue<Boolean> multiValued);

        /**
         * Specifies whether the attribute is creatable.
         *
         * @param creatable the creatable flag with metadata
         * @return this mapping instance for chaining
         */
        F creatable(DefinitionValue<Boolean> creatable);

        /**
         * Specifies whether the attribute is updatable.
         *
         * @param updatable the updatable flag with metadata
         * @return this mapping instance for chaining
         */
        F updatable(DefinitionValue<Boolean> updatable);

        /**
         * Sets the role of this attribute within a reference context.
         *
         * @param detected the role name with metadata (e.g. "SUBJECT", "OBJECT")
         * @return this mapping instance for chaining
         */
        F roleInReference(DefinitionValue<String> detected);

        /**
         * Sets the name of the referenced object class.
         *
         * @param complexType the referenced object class with metadata
         * @return this mapping instance for chaining
         */
        F referencedObjectClassName(DefinitionValue<String> complexType);

        /**
         * Sets a subtype qualifier for this attribute.
         *
         * @param from the subtype value with metadata
         * @return this mapping instance for chaining
         */
        F subtype(DefinitionValue<String> from);

        /**
         * Returns the ConnId type.
         *
         * @return the type definition value (never null)
         */
        DefinitionValue<Class<?>> type();
    }
}