/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.schema;

import com.evolveum.polygon.conndev.api.AttributePath;
import com.evolveum.polygon.conndev.api.ContextLookup;
import com.evolveum.polygon.conndev.build.api.AttributeBuilder;
import com.evolveum.polygon.conndev.build.api.ValueMappingBuilder;
import com.evolveum.polygon.conndev.concepts.DefinitionValue;
import com.evolveum.polygon.conndev.concepts.GroovyClosures;
import com.evolveum.polygon.conndev.concepts.SourceLocation;
import com.evolveum.polygon.conndev.json.JsonAttributeMapping;
import com.evolveum.polygon.conndev.json.OpenApiValueMapping;
import com.evolveum.polygon.conndev.spi.AttributeProtocolMapping;
import com.evolveum.polygon.conndev.spi.EmbeddedObjectJsonMapping;
import com.evolveum.polygon.conndev.spi.ValueMapping;
import com.fasterxml.jackson.databind.JsonNode;
import groovy.lang.Closure;
import groovy.lang.DelegatesTo;
import org.identityconnectors.framework.common.objects.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Abstract base implementation of {@link AttributeBuilder} that manages ConnId metadata
 * and protocol mapping builders (JSON, embedded object).
 *
 * <p>This class implements the core attribute builder logic including:
 * managing the ConnId-side metadata via an inner {@link ConnIdBuilder}, providing
 * JSON protocol mapping via an inner {@link JsonBuilder}, and tracking embedded object
 * complex types.</p>
 *
 * <p>Subclasses can override {@link #newProtocolMapping(Class)} to inject custom protocol
 * mapping implementations.</p>
 *
 * @param <B> The concrete builder type (CRTP self-type)
 * @param <A> The public attribute builder interface
 * @param <P> The attribute definition type produced by {@code build()}
 */
public abstract class AbstractAttributeBuilder<B extends AbstractAttributeBuilder<B,A,P>, A extends AttributeBuilder<A,P>, P> implements AttributeBuilder<A, P> {


    /**
     * Attribute name used to identify this attribute in object class definition
     *
     * Usually it is same as application attribute name and protocol attribute name.
     *
     */
    DefinitionValue<String> name;


    /**
     * The ConnId-side attribute metadata builder.
     *
     * Tracks ConnId attribute name, type, multiplicity, readability,
     * required flag, creatable/updatable flags, and role within relationships.
     */
    final ConnIdBuilder connIdBuilder;

    /**
     * The parent object class builder that owns this attribute.
     */
    final BaseObjectClassDefinitionBuilder objectClass;

    /**
     * Registry of protocol-specific mappings keyed by mapping class (e.g., {@link JsonAttributeMapping}).
     */
    Map<Class<? extends AttributeProtocolMapping<?,?>>, AttributeProtocolMappingBuilder> protocolMappings = new HashMap<>();


    /**
     * Whether this attribute is emulated (resolved at runtime rather than exposed in remote metadata).
     */
    DefinitionValue<Boolean> emulated = DefinitionValue.DEFAULT_FALSE;

    /**
     * The remote (protocol-side) name of the attribute, captured from the definition value.
     */
    DefinitionValue<String> remoteName;

    /**
     * The ConnId Java type. Null until set via a protocol mapping or explicit type definition.
     */
    Class<?> connIdType;

    /**
     * The complex type (referenced object class name) for embedded objects.
     */
    private DefinitionValue<String> complexType = DefinitionValue.emptyDefault();

    /**
     * Creates a new attribute builder for the given name within the specified object class.
     *
     * @param restObjectClassBuilder the parent object class builder
     * @param name the attribute name definition
     */
    protected AbstractAttributeBuilder(BaseObjectClassDefinitionBuilder restObjectClassBuilder, DefinitionValue<String> name) {
        this.name = name;
        this.remoteName = name.asDefault();
        this.connIdBuilder = new ConnIdBuilder(name.asDefault());
        this.objectClass = restObjectClassBuilder;
    }

    /**
     * Sets the remote name (protocol-side attribute name).
     *
     * @param protocolName the protocol name definition
     * @return this builder for chaining
     */
    @Override
    public A protocolName(DefinitionValue<String> protocolName) {
        this.remoteName = this.remoteName.moreSpecific(protocolName);
        return self();
    }

    /**
     * Associates this attribute with a complex type (embedded object).
     *
     * <p>When a complex type is specified, the ConnId type is automatically set to
     * {@link EmbeddedObject} and the role is set to SUBJECT. A JSON mapping using
     * {@link EmbeddedObjectJsonMapping} is also configured.</p>
     *
     * @param objectClass the object class of the referenced type
     * @return this builder for chaining
     */
    @Override
    public A complexType(DefinitionValue<String> objectClass) {
        this.complexType = complexType.moreSpecific(objectClass);
        if (complexType.isPresent()) {
            // FIX: This logic should be moved somewhere else
            connId().type(DefinitionValue.detected(EmbeddedObject.class));
            connId().roleInReference(DefinitionValue.detected(AttributeInfo.RoleInReference.SUBJECT.toString()));
            connId().referencedObjectClassName(this.complexType);
            // FIX: Should use definition value pattern
            json().implementation(new EmbeddedObjectJsonMapping(contextLookup(), objectClass.value()));
        }
        return self();
    }

    /**
     * Marks this attribute as emulated (resolved at runtime).
     *
     * @param emulated the emulated flag with metadata
     * @return this builder for chaining
     */
    @Override
    public A emulated(DefinitionValue<Boolean> emulated) {
        this.emulated = this.emulated.moreSpecific(emulated);
        return self();
    }

    /**
     * Sets the remote name (the name as it appears in the protocol representation).
     *
     * @param remoteName the remote name definition
     * @return this builder for chaining
     */
    @Override
    public A remoteName(DefinitionValue<String> remoteName) {
        this.remoteName = this.remoteName.moreSpecific(remoteName);
        return self();
    }

    /**
     * Returns the context lookup for attribute value resolution.
     *
     * @return the context lookup
     */
    protected ContextLookup contextLookup() {
        return objectClass.contextLookup();
    }

    /**
     * Returns the JSON protocol mapping builder, creating it if necessary.
     *
     * @return the JSON mapping builder
     */
    @Override
    public JsonMapping json() {
        return (JsonBuilder) protocolMappings.computeIfAbsent(JsonAttributeMapping.class, m -> new JsonBuilder());
    }

    /**
     * Configures the JSON protocol mapping via a closure.
     *
     * @param closure the closure for configuring the JSON mapping
     * @return the JSON mapping builder
     */
    @Override
    public JsonMapping json(@DelegatesTo(value = JsonMapping.class, strategy = Closure.DELEGATE_ONLY) Closure<?> closure) {
        return GroovyClosures.callAndReturnDelegate(closure,json());
    }

    /**
     * Returns the ConnId-side attribute metadata builder.
     *
     * @return the ConnId builder
     */
    @Override
    public ConnIdMapping connId() {
        return connIdBuilder;
    }

    /**
     * ConnId-side attribute metadata builder.
     *
     * <p>Holds the complete ConnId attribute definition including name, type, multiplicity,
     * readability, required/creatable/updatable flags, and role within relationships.</p>
     */
    class ConnIdBuilder implements ConnIdMapping {

        /** The ConnId attribute name. */
        private DefinitionValue<String> name;
        /** The ConnId attribute native (protocol-side) name. */
        private DefinitionValue<String> nativeName;
        /** The ConnId Java type of the attribute. */
        private DefinitionValue<Class<?>> type = DefinitionValue.defaultFrom(String.class);
        /** Whether the attribute is readable. */
        private DefinitionValue<Boolean> readable = DefinitionValue.DEFAULT_TRUE;
        /** Whether the attribute is required. */
        private DefinitionValue<Boolean> required = DefinitionValue.DEFAULT_FALSE;
        /** The attribute description. */
        private DefinitionValue<String> description = DefinitionValue.emptyDefault();
        /** Whether the attribute is returned by default. */
        private DefinitionValue<Boolean> returnedByDefault = DefinitionValue.DEFAULT_TRUE;
        /** Whether the attribute is multi-valued. */
        private DefinitionValue<Boolean> multiValued = DefinitionValue.DEFAULT_FALSE;
        /** Whether the attribute is creatable. */
        private DefinitionValue<Boolean> creatable = DefinitionValue.DEFAULT_TRUE;
        /** Whether the attribute is updatable. */
        private DefinitionValue<Boolean> updatable = DefinitionValue.DEFAULT_TRUE;
        /** The role within a reference relationship (e.g., SUBJECT, REFERENCED). */
        private DefinitionValue<String> roleInReference = DefinitionValue.emptyDefault();
        /** The referenced object class name for reference relationships. */
        private DefinitionValue<String> referencedObjectClassName = DefinitionValue.emptyDefault();
        /** The ConnId attribute subtype. */
        private DefinitionValue<String> subtype = DefinitionValue.emptyDefault();

        /**
         * Creates a new ConnId attribute builder with the given name.
         *
         * @param name the attribute name definition
         */
        public ConnIdBuilder(DefinitionValue<String> name) {
            this.name = name;
            this.nativeName = name;
        }

        @Override
        public ConnIdMapping name(String name) {
            name(DefinitionValue.from(name, SourceLocation.capture()));
            return this;
        }

        @Override
        public ConnIdMapping name(DefinitionValue<String> name) {
            this.name = this.name.moreSpecific(name);
            return self();
        }

        /**
         * Returns the ConnId Java type.
         *
         * @return the type definition value
         */
        @Override
        public DefinitionValue<Class<?>> type() {
            return this.type;
        }

        @Override
        public ConnIdMapping type(DefinitionValue<Class<?>> connIdType) {
            this.type = this.type.moreSpecific(connIdType);
            return self();
        }

        @Override
        public ConnIdMapping readable(DefinitionValue<Boolean> readable) {
            this.readable = this.readable.moreSpecific(readable);
            return self();
        }

        @Override
        public ConnIdMapping required(DefinitionValue<Boolean> required) {
            this.required = this.required.moreSpecific(required);
            return self();
        }

        @Override
        public ConnIdMapping description(DefinitionValue<String> description) {
            this.description = this.description.moreSpecific(description);
            return self();
        }

        @Override
        public ConnIdMapping returnedByDefault(DefinitionValue<Boolean> returnedByDefault) {
            this.returnedByDefault = this.returnedByDefault.moreSpecific(returnedByDefault);
            return self();
        }

        @Override
        public ConnIdMapping multiValued(DefinitionValue<Boolean> multiValued) {
            this.multiValued = this.multiValued.moreSpecific(multiValued);
            return self();
        }

        @Override
        public ConnIdMapping creatable(DefinitionValue<Boolean> creatable) {
            this.creatable = this.creatable.moreSpecific(creatable);
            return self();
        }

        @Override
        public ConnIdMapping updatable(DefinitionValue<Boolean> updatable) {
            this.updatable = this.updatable.moreSpecific(updatable);
            return self();
        }

        @Override
        public ConnIdMapping roleInReference(DefinitionValue<String> detected) {
            this.roleInReference = this.roleInReference.moreSpecific(detected);
            return self();
        }

        @Override
        public ConnIdMapping referencedObjectClassName(DefinitionValue<String> complexType) {
            this.referencedObjectClassName = this.referencedObjectClassName.moreSpecific(complexType);
            return self();
        }

        @Override
        public ConnIdMapping nativeName(DefinitionValue<String> aDefault) {
            this.nativeName = this.nativeName.moreSpecific(aDefault);
            return self();
        }

        public ConnIdMapping subtype(DefinitionValue<String> from) {
            this.subtype = this.subtype.moreSpecific(from);
            return self();
        }

        /**
         * Builds the {@link AttributeInfo} from all configured ConnId metadata,
         * automatically setting the type to {@Link String} for canonical Uid and Name attributes.
         *
         * @return the built AttributeInfo
         */
        public AttributeInfo build() {
            var builder = new AttributeInfoBuilder();

            if (Uid.NAME.equals(name.value())) {
                connId().type(String.class);
            }
            if (Name.NAME.equals(name.value())) {
                connId().type(String.class);
            }



            builder.setType(type.value());
            builder.setName(name.value());
            builder.setNativeName(nativeName.value());
            builder.setReadable(readable.value());
            if (Boolean.FALSE.equals(readable.value())) {
                returnedByDefault(readable.derived(false));
            }
            builder.setRequired(required.value());
            builder.setDescription(description.value());
            builder.setReturnedByDefault(returnedByDefault.value());
            builder.setMultiValued(multiValued.value());
            builder.setCreateable(creatable.value());
            builder.setUpdateable(updatable.value());
            builder.setRoleInReference(roleInReference.value());
            builder.setReferencedObjectClassName(referencedObjectClassName.value());
            builder.setSubtype(subtype.value());
            return builder.build();
        }

    }

    class JsonBuilder implements AttributeProtocolMappingBuilder, JsonMapping {

        /** The protocol-side name of this JSON mapping. */
        private String name;
        /** The JSON path for navigating to the attribute value. */
        private AttributePath path;
        /** The JSON type string (e.g., "string", "integer", "boolean"). */
        private String type;
        /** The OpenAPI format for typed JSON values (e.g., "date-time", "email"). */
        private String openApiFormat;
        /** The JSON value mapping implementation. */
        private ValueMapping<Object, JsonNode> implementation;


        /**
         * Creates a new JSON mapping builder with the remote name as default.
         */
        JsonBuilder() {
            this.name = remoteName.value();
        }

        @Override
        public JsonMapping name(String protocolName) {
            this.name = protocolName;
            return this;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public JsonMapping type(String jsonType) {
            type = jsonType;
            return this;
        }

        @Override
        public JsonMapping path(AttributePath path) {
            this.path = path;
            return this;
        }

        @Override
        public JsonMapping openApiFormat(String openapiFormat) {
            this.openApiFormat = openapiFormat;
            return this;
        }

        @Override
        public JsonMapping implementation(ValueMapping<?, JsonNode> mapping) {
            this.implementation = (ValueMapping) mapping;
            return this;
        }

        @Override
        public JsonMapping implementation(@DelegatesTo(ValueMappingBuilder.class) Closure<?> closure) {
            Class<?> typeClass = connIdType != null ? connIdType : Object.class;
            var builder = new BaseValueMappingBuilder<>(typeClass,JsonNode.class);
            GroovyClosures.callAndReturnDelegate(closure, builder);
            this.implementation = (ValueMapping) builder.build();
            return this;
        }

        @Override
        public MappingTableBuilder mappingTable() {
            // TODO: Implement later
            return null;
        }

        @Override
        public MappingTableBuilder mappingTable(Closure<?> closure) {
            // TODO: Implement later
            return null;
        }

        /**
         * Returns the suggested ConnId type for this JSON mapping.
         *
         * @return always null (not yet implemented)
         */
        @Override
        public Class<?> suggestedConnIdType() {
            return null;
        }

        /**
         * Builds the {@link JsonAttributeMapping}. If no implementation is set, creates
         * one from the JSON type and OpenAPI format. Applies a {@link ValueTypeOverrideMapping}
         * if the ConnId type differs from the implementation's native type.
         *
         * @return the built JsonAttributeMapping
         */
        @Override
        public AttributeProtocolMapping<?,?> build() {

            if (this.implementation == null) {
                implementation = OpenApiValueMapping.from(type, openApiFormat);
            }
            if (connIdType != null && !connIdType.equals(implementation.connIdType())) {
                // apply ConnId type override
                implementation = ValueTypeOverrideMapping.of(connIdType, implementation);
            }
            if (path == null) {
                path = AttributePath.of(name);
            }
            return new JsonAttributeMapping(path, implementation);
        }
    }
}
