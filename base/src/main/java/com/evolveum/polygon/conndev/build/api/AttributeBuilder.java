/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.build.api;

import com.evolveum.polygon.conndev.annotations.Groovy;
import com.evolveum.polygon.conndev.api.AttributePath;
import com.evolveum.polygon.conndev.build.ConnIdBuiltInAttribute;
import com.evolveum.polygon.conndev.build.spi.SpiAttributeBuilder;
import com.evolveum.polygon.conndev.concepts.DefinitionValue;
import com.evolveum.polygon.conndev.concepts.GroovyClosures;
import com.evolveum.polygon.conndev.concepts.SourceLocation;
import com.evolveum.polygon.conndev.spi.ValueMapping;
import com.fasterxml.jackson.databind.JsonNode;
import groovy.lang.Closure;
import groovy.lang.DelegatesTo;

/**
 * Primary interface for defining attribute configuration in the connector DSL.
 *
 * <p>This interface provides a fluent builder for specifying attribute metadata including
 * ConnId metadata (readable, required, creatable, updatable, multiplicity), protocol-specific
 * mappings (JSON/remote-side name, type, OpenAPI format), and whether the attribute is
 * emulated (resolved at runtime rather than stored in remote metadata).</p>
 *
 * @param <B> The concrete builder type (self-type for CRTP)
 * @param <P> The product type
 */
public interface AttributeBuilder<B extends AttributeBuilder<B, P>, P> extends SpiAttributeBuilder<B,P> {

    /**
     * Sets the readability of the attribute.
     *
     * When the attribute is marked as not readable it also disables the attribute from being returned by default.
     *
     * @param readable true if the attribute should be readable, false otherwise
     * @return the current instance of {@code AttributeBuilder<B, P>} for method chaining
     */
    default B readable(boolean readable) {
        connId().readable(DefinitionValue.from(readable, SourceLocation.capture()));
        return self();
    }

    /**
     * Specifies whether the attribute is required.
     *
     * Required attributes must be provided when creating or updating objects. They cannot be omitted.
     *
     * @param required true if the attribute should be required, false otherwise
     * @return the current instance of {@code AttributeBuilder} for method chaining
     */
    default B required(boolean required) {
        connId().required(DefinitionValue.from(required, SourceLocation.capture()));
        return self();
    }

    /**
     * Sets the attribute description.
     *
     * @param description the description text
     * @return the current instance for method chaining
     */
    default B description(String description) {
        connId().description(DefinitionValue.from(description, SourceLocation.capture()));
        return self();
    }

    /**
     * Specifies whether the attribute is returned by default in list/read operations.
     *
     * @param returnedByDefault true if the attribute should be included by default, false otherwise
     * @return the current instance for method chaining
     */
    default B returnedByDefault(boolean returnedByDefault) {
        connId().returnedByDefault(DefinitionValue.from(returnedByDefault, SourceLocation.capture()));
        return self();
    }

    /**
     * Specifies whether the attribute is multi-valued (can hold multiple values).
     *
     * @param multiValued true if the attribute accepts multiple values, false otherwise
     * @return the current instance for method chaining
     */
    default B multiValued(boolean multiValued) {
        connId().multiValued(DefinitionValue.from(multiValued, SourceLocation.capture()));
        return self();
    }

    /**
     * Specifies whether the attribute is creatable.
     *
     * @param creatable true if the attribute should be creatable, false otherwise
     * @return the current instance for method chaining
     */
    default B creatable(boolean creatable) {
        connId().creatable(DefinitionValue.from(creatable, SourceLocation.capture()));
        return self();
    }

    /**
     * Specifies whether the attribute is updatable.
     *
     * @param updatable true if the attribute should be updatable, false otherwise
     * @return the current instance for {@code AttributeBuilder} for method chaining
     */
    default B updatable(boolean updatable) {
        connId().updatable(DefinitionValue.from(updatable, SourceLocation.capture()));
        return self();
    }


    /**
     * British spelling variant of {@link #updatable(boolean)}.
     *
     * @param updatable true if the attribute should be updatable, false otherwise
     * @return the current instance for method chaining
     */
    @Groovy.AlternateSpelling
    default B updateable(boolean updatable) {
        return updatable(updatable);
    }

    /**
     * Specifies whether the attribute is emulated (resolved via a runtime resolver rather than
     * exposed in the remote system's metadata).
     *
     * @param emulated true if the attribute should be emulated, false otherwise
     * @return the current instance for method chaining
     */
    default B emulated(boolean emulated) {
        return emulated(DefinitionValue.from(emulated, SourceLocation.capture()));
    }


    // Protocol specific mappings

    /**
     * Returns the JSON protocol mapping builder for this attribute.
     *
     * @return a JSON mapping builder instance
     */
    JsonMapping json();

    /**
     * Configures the JSON protocol mapping for this attribute via a closure.
     *
     * @param closure a closure that configures the {@link JsonMapping} instance
     * @return the configured JSON mapping instance
     */
    JsonMapping json(@DelegatesTo(value = JsonMapping.class, strategy = Closure.DELEGATE_ONLY) Closure<?> closure);

    /**
     * Returns the ConnId attribute metadata builder for this attribute.
     *
     * @return the ConnId mapping instance
     */
    ConnIdMapping connId();

    /**
     * Configures the ConnId attribute metadata via a closure.
     *
     * @param closure a closure that configures the {@link ConnIdMapping} instance
     * @return the configured ConnId mapping instance
     */
    default ConnIdMapping connId(@DelegatesTo(ConnIdMapping.class) Closure<?> closure) {
        return GroovyClosures.callAndReturnDelegate(closure, connId());
    }

/**
     * Sets the protocol name for the current attribute.
     *
     * The protocol name represents the name of the attribute as present in serialized form
     * (e.g., JSON key or XML tag).
     *
     * @param protocolName the protocol name to be set for the attribute
     * @return the current instance of {@code AttributeBuilder} for method chaining
     * @deprecated Use {@link #connId()} to set the ConnId-side name instead. The remote/protocol name
     * is configured via {@link JsonMapping#name(String)}.
     */
    @Deprecated
    default B protocolName(String protocolName) {
        return protocolName(DefinitionValue.from(protocolName, SourceLocation.capture()));
    }

    /**
     * Sets the remote name of the attribute.
     *
     * The remote name of the attribute represents the attribute name in the remote system.
     * This may differ from {@link #protocolName(String)}.
     *
     * @param remoteName the name of the attribute in the remote system
     * @return the current instance of {@code AttributeBuilder} for method chaining
     * @deprecated Use {@link JsonMapping#name(String)} to set the protocol/remote name on the JSON mapping.
     */
    @Deprecated
    default B remoteName(String remoteName) {
        return remoteName(DefinitionValue.from(remoteName, SourceLocation.capture()));
    }

    /**
     * Sets the JSON type of the attribute.
     *
     * The JSON type specifies the data type of the attribute when represented in JSON format.
     *
     * @param jsonType the JSON type of the attribute (e.g., "string", "integer", "boolean")
     * @return the current instance of {@code AttributeBuilder} for method chaining
     * @deprecated Use {@link JsonMapping#type(String)} directly instead.
     */
    @Deprecated
    default B jsonType(String jsonType) {
        json().type(jsonType);
        return self();
    }

    /**
     * Associates this attribute with an object class (complex type).
     *
     * When an object class is specified, the attribute is treated as an embedded object reference
     * and its ConnId type is automatically set to {@link org.identityconnectors.framework.common.objects.ConnectorObjectReference}.
     *
     * @param objectClass the object class name for this complex type attribute
     * @return the current instance for method chaining
     */
    default B complexType(String objectClass) {
        return complexType(DefinitionValue.from(objectClass, SourceLocation.capture()));
    }

    /**
     * Sets the OpenAPI format for the JSON representation of this attribute.
     *
     * The format specifies how the value should be interpreted according to the OpenAPI specification
     * (e.g. {@code date-time}, {@code email}, {@code uuid}).
     *
     * @param openapiFormat the OpenAPI format string
     * @return the current instance for method chaining
     * @deprecated Use {@link JsonMapping#openApiFormat(String)} directly instead.
     */
    @Deprecated
    default B openApiFormat(String openapiFormat) {
        json().openApiFormat(openapiFormat);
        return self();
    }

    interface MappingBuilder<T extends MappingBuilder<T>> {
        /**
         * Sets a custom value mapping implementation for JSON serialization/deserialization.
         *
         * @param mapping the value mapping to use
         * @return this mapping builder
         */
        T implementation(ValueMapping<?, JsonNode> mapping);

        /**
         * Sets a custom value mapping implementation via a closure.
         *
         * @param closure a closure that configures the value mapping
         * @return this mapping builder
         */
        T implementation(@DelegatesTo(ValueMappingBuilder.class) Closure<?> closure);

        /**
         * Creates a mapping table for discrete value translations.
         *
         * @return the mapping table builder
         */
        MappingTableBuilder mappingTable();

        /**
         * Configures a mapping table via a closure.
         *
         * @param closure a closure that configures the mapping table
         * @return the mapping table builder
         */
        MappingTableBuilder mappingTable(@DelegatesTo(value = MappingTableBuilder.class) Closure<?> closure);
    }

    interface MappingTableBuilder {
        /**
         * Adds a key-value pair to the mapping table.
         *
         * @param remote the value as it appears in the remote system
         * @param local the value in ConnId representation
         * @return this mapping table builder
         */
        MappingTableBuilder pair(Object remote, Object local);
    }

    /**
     * JSON protocol mapping builder.
     *
     * Configures the JSON-side representation of an attribute, including the JSON field name,
     * data type, OpenAPI format, and JSON path for nested attributes.
     */
    interface JsonMapping extends MappingBuilder<JsonMapping> {
        /**
         * Returns the JSON field name.
         *
         * @return the JSON field name
         */
        String name();
        /**
         * Sets the JSON field name.
         *
         * @param protocolName the JSON field name
         * @return this JSON mapping instance
         */
        JsonMapping name(String protocolName);
        /**
         * Sets the JSON type.
         *
         * @param jsonType the JSON data type (e.g., "string", "integer")
         * @return this JSON mapping instance
         */
        JsonMapping type(String jsonType);
        /**
         * Sets the OpenAPI format.
         *
         * @param openapiFormat the OpenAPI format (e.g., "date-time", "email")
         * @return this JSON mapping instance
         */
        JsonMapping openApiFormat(String openapiFormat);

        /**
         * Convenience method to create an {@link AttributePath} for a simple field.
         *
         * @param name the field name
         * @return an AttributePath pointing to the named field
         */
        default AttributePath attribute(String name) {
            return AttributePath.of(name);
        }

        /**
         * Sets a JSON path for nested attribute access.
         *
         * @param path the JSON path (supports nested fields, arrays, and filters)
         * @return this JSON mapping instance
         */
        JsonMapping path(AttributePath path);
    }

    /**
     * ConnId attribute metadata builder.
     *
     * Configures the ConnId-side metadata for an attribute, including the ConnId attribute name,
     * type, multiplicity, and operational flags (readable, creatable, updatable, etc.).
     */
    interface ConnIdMapping extends SpiAttributeBuilder.ConnIdMapping<ConnIdMapping>, ConnIdBuiltInAttribute.Mixin {

        /**
         * Sets the ConnId attribute name (convenience for {@code name(DefinitionValue)}).
         *
         * @param name the ConnId attribute name
         * @return this ConnId mapping instance
         */
        default ConnIdMapping name(String name) {
            return name(DefinitionValue.from(name, SourceLocation.capture()));
        }

        /**
         * Sets the ConnId Java type (convenience for {@code type(DefinitionValue)}).
         *
         * @param connIdType the ConnId Java class
         * @return this ConnId mapping instance
         */
        default ConnIdMapping type(Class<?> connIdType) {
            return type(DefinitionValue.from(connIdType, SourceLocation.capture()));
        }

        /**
         * Specifies if the attribute is required (convenience for {@code required(DefinitionValue)}).
         *
         * @param required true if required, false otherwise
         * @return this ConnId mapping instance
         */
        default ConnIdMapping required(boolean required) {
            return required(DefinitionValue.from(required, SourceLocation.capture()));
        }

        /**
         * Sets the attribute description (convenience for {@code description(DefinitionValue)}).
         *
         * @param description the description text
         * @return this ConnId mapping instance
         */
        default ConnIdMapping description(String description) {
            return description(DefinitionValue.from(description, SourceLocation.capture()));
        }

        /**
         * Specifies if returned by default (convenience for {@code returnedByDefault(DefinitionValue)}).
         *
         * @param returnedByDefault true if returned by default, false otherwise
         * @return this ConnId mapping instance
         */
        default ConnIdMapping returnedByDefault(boolean returnedByDefault) {
            return returnedByDefault(DefinitionValue.from(returnedByDefault, SourceLocation.capture()));
        }

        /**
         * Specifies if multi-valued (convenience for {@code multiValued(DefinitionValue)}).
         *
         * @param multiValued true if multi-valued, false otherwise
         * @return this ConnId mapping instance
         */
        default ConnIdMapping multiValued(boolean multiValued) {
            return multiValued(DefinitionValue.from(multiValued, SourceLocation.capture()));
        }

        /**
         * Sets the native attribute name (convenience for {@code nativeName(DefinitionValue)}).
         *
         * @param name the native name
         * @return this ConnId mapping instance
         */
        default ConnIdMapping nativeName(String name) {
            return nativeName(DefinitionValue.from(name, SourceLocation.capture()));
        }
    }

}
