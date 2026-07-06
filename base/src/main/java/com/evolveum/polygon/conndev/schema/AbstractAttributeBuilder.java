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

public abstract class AbstractAttributeBuilder<B extends AbstractAttributeBuilder<B,P>, P> implements AttributeBuilder<B, P> {

    BaseObjectClassDefinitionBuilder objectClass;

    final ConnIdBuilder connIdBuilder;

    Map<Class<? extends AttributeProtocolMapping<?,?>>, AttributeProtocolMappingBuilder> protocolMappings = new HashMap<>();


    DefinitionValue<Boolean> emulated = DefinitionValue.DEFAULT_FALSE;

    DefinitionValue<String> remoteName;
    Class<?> connIdType;

    private DefinitionValue<String> complexType = DefinitionValue.emptyDefault();

    protected AbstractAttributeBuilder(BaseObjectClassDefinitionBuilder restObjectClassBuilder, DefinitionValue<String> name) {
        this.remoteName = name;
        this.connIdBuilder = new ConnIdBuilder(name.asDefault());
        this.objectClass = restObjectClassBuilder;
    }

    @Override
    public B protocolName(DefinitionValue<String> protocolName) {
        this.remoteName = this.remoteName.moreSpecific(protocolName);
        return self();
    }

    @Override
    public B complexType(DefinitionValue<String> objectClass) {
        this.complexType = complexType.moreSpecific(objectClass);
        if (complexType.isPresent()) {
            // FIXME: THis should be moved somewhere else
            connId().type(DefinitionValue.detected(EmbeddedObject.class));
            connId().roleInReference(DefinitionValue.detected(AttributeInfo.RoleInReference.SUBJECT.toString()));
            connId().referencedObjectClassName(this.complexType);
            // FIXME: Should be definition value
            json().implementation(new EmbeddedObjectJsonMapping(contextLookup(), objectClass.value()));
        }
        return self();
    }

    @Override
    public B emulated(DefinitionValue<Boolean> emulated) {
        this.emulated = this.emulated.moreSpecific(emulated);
        return self();
    }

    @Override
    public B remoteName(DefinitionValue<String> remoteName) {
        this.remoteName = this.remoteName.moreSpecific(remoteName);
        return self();
    }

    protected ContextLookup contextLookup() {
        return objectClass.contextLookup();
    }

    @Override
    public JsonMapping json() {
        return (JsonBuilder) protocolMappings.computeIfAbsent(JsonAttributeMapping.class, m -> new JsonBuilder());
    }

    @Override
    public JsonMapping json(@DelegatesTo(value = JsonMapping.class, strategy = Closure.DELEGATE_ONLY) Closure<?> closure) {
        return GroovyClosures.callAndReturnDelegate(closure,json());
    }

    @Override
    public ConnIdMapping connId() {
        return connIdBuilder;
    }

    class ConnIdBuilder implements ConnIdMapping {

        private DefinitionValue<String> name;
        private DefinitionValue<String> nativeName;

        private DefinitionValue<Class<?>> type = DefinitionValue.defaultFrom(String.class);
        private DefinitionValue<Boolean> readable = DefinitionValue.DEFAULT_TRUE;
        private DefinitionValue<Boolean> required = DefinitionValue.DEFAULT_FALSE;
        private DefinitionValue<String> description = DefinitionValue.emptyDefault();
        private DefinitionValue<Boolean> returnedByDefault = DefinitionValue.DEFAULT_TRUE;
        private DefinitionValue<Boolean> multiValued = DefinitionValue.DEFAULT_FALSE;
        private DefinitionValue<Boolean> creatable = DefinitionValue.DEFAULT_TRUE;
        private DefinitionValue<Boolean> updatable = DefinitionValue.DEFAULT_TRUE;
        private DefinitionValue<String> roleInReference = DefinitionValue.emptyDefault();
        private DefinitionValue<String> referencedObjectClassName = DefinitionValue.emptyDefault();
        private DefinitionValue<String> subtype = DefinitionValue.emptyDefault();

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

        private String name;
        private AttributePath path;
        private String type;
        private String openApiFormat;
        private ValueMapping<Object, JsonNode> implementation;


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
            // FIXME: Implement later
            return null;
        }

        @Override
        public MappingTableBuilder mappingTable(Closure<?> closure) {
            // FIXME: Implement later
            return null;
        }

        @Override
        public Class<?> suggestedConnIdType() {
            return null;
        }

        @Override
        public AttributeProtocolMapping<?,?> build() {

            if (this.implementation == null) {
                implementation = OpenApiValueMapping.from(type, openApiFormat);
            }
            if (connIdType != null && !connIdType.equals(implementation.connIdType())) {
                // we need to to ConnID override
                implementation = ValueTypeOverrideMapping.of(connIdType, implementation);
            }
            if (path == null) {
                path = AttributePath.of(name);
            }
            return new JsonAttributeMapping(path, implementation);
        }
    }
}
