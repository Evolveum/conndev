/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.schema;

import com.evolveum.polygon.conndev.api.ContextLookup;
import com.evolveum.polygon.conndev.json.JsonAttributeMapping;
import com.evolveum.polygon.conndev.json.OpenApiValueMapping;
import com.evolveum.polygon.conndev.spi.ValueMapping;
import com.evolveum.polygon.conndev.api.AttributePath;
import com.evolveum.polygon.conndev.concepts.GroovyClosures;
import com.evolveum.polygon.conndev.build.AttributeBuilder;
import com.evolveum.polygon.conndev.build.ValueMappingBuilder;
import com.evolveum.polygon.conndev.spi.AttributeProtocolMapping;
import com.evolveum.polygon.conndev.spi.EmbeddedObjectJsonMapping;
import com.fasterxml.jackson.databind.JsonNode;
import groovy.lang.Closure;
import groovy.lang.DelegatesTo;
import org.identityconnectors.framework.common.objects.AttributeInfo;
import org.identityconnectors.framework.common.objects.AttributeInfoBuilder;
import org.identityconnectors.framework.common.objects.EmbeddedObject;

import java.util.HashMap;
import java.util.Map;

public abstract class AbstractAttributeBuilder implements AttributeBuilder {

    BaseObjectClassDefinitionBuilder objectClass;
    AttributeInfoBuilder connIdBuilder = new AttributeInfoBuilder();

    Map<Class<? extends AttributeProtocolMapping<?,?>>, AttributeProtocolMappingBuilder> protocolMappings = new HashMap<>();


    boolean emulated = false;

    String remoteName;
    String connIdName;
    Class<?> connIdType;

    private String complexType;

    protected AbstractAttributeBuilder(BaseObjectClassDefinitionBuilder restObjectClassBuilder, String name) {
        this.remoteName = name;
        connIdBuilder.setName(name);
        connIdBuilder.setNativeName(name);
        this.objectClass = restObjectClassBuilder;
    }


    @Override
    public AbstractAttributeBuilder protocolName(String protocolName) {
        json().name(protocolName);
        return this;
    }

    @Override
    public AbstractAttributeBuilder remoteName(String remoteName) {
        this.remoteName = remoteName;
        return this;
    }

    @Override
    public void emulated(boolean emulated) {
        this.emulated = emulated;
    }

    @Override
    public AbstractAttributeBuilder readable(boolean readable) {
        connIdBuilder.setReadable(readable);
        if (!readable) {
            connIdBuilder.setReturnedByDefault(false);
        }
        return this;
    }


    @Override
    public AbstractAttributeBuilder required(boolean required) {
        connIdBuilder.setRequired(required);
        return this;
    }

    @Override
    public AbstractAttributeBuilder updatable(boolean updatable) {
        connIdBuilder.setUpdateable(updatable);
        return this;
    }

    @Override
    public AbstractAttributeBuilder creatable(boolean creatable) {
        connIdBuilder.setCreateable(creatable);
        return this;
    }

    @Override
    public AbstractAttributeBuilder description(String description) {
        connIdBuilder.setDescription(description);
        return this;
    }


    @Override
    public void complexType(String objectClass) {
        this.complexType = objectClass;
        connId().type(EmbeddedObject.class);
        connIdBuilder.setRoleInReference(AttributeInfo.RoleInReference.SUBJECT.toString());
        connIdBuilder.setReferencedObjectClassName(objectClass);
        json().implementation(new EmbeddedObjectJsonMapping(contextLookup(), objectClass));
    }

    protected ContextLookup contextLookup() {
        return objectClass.contextLookup();
    }


    @Override
    public AbstractAttributeBuilder returnedByDefault(boolean returnedByDefault) {
        connIdBuilder.setReturnedByDefault(returnedByDefault);
        return this;
    }

    @Override
    public AbstractAttributeBuilder multiValued(boolean multiValued) {
        connIdBuilder.setMultiValued(multiValued);
        return this;
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
        return new ConnIdMapping() {
            @Override
            public ConnIdMapping name(String name) {
                AbstractAttributeBuilder.this.connIdName = name;
                AbstractAttributeBuilder.this.connIdBuilder.setName(name);
                return this;
            }

            @Override
            public ConnIdMapping type(Class<?> connIdType) {
                AbstractAttributeBuilder.this.connIdType = connIdType;
                AbstractAttributeBuilder.this.connIdBuilder.setType(connIdType);
                return this;
            }
        };
    }

    class JsonBuilder implements AttributeProtocolMappingBuilder, JsonMapping {

        private String name;
        private AttributePath path;
        private String type;
        private String openApiFormat;
        private ValueMapping<Object, JsonNode> implementation;


        JsonBuilder() {
            this.name = remoteName;
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
