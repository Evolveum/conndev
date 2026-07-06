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
import com.evolveum.polygon.conndev.json.JsonAttributeMapping;
import com.evolveum.polygon.conndev.json.OpenApiValueMapping;
import com.evolveum.polygon.conndev.spi.AttributeProtocolMapping;
import com.evolveum.polygon.conndev.spi.EmbeddedObjectJsonMapping;
import com.evolveum.polygon.conndev.spi.ValueMapping;
import com.fasterxml.jackson.databind.JsonNode;
import groovy.lang.Closure;
import groovy.lang.DelegatesTo;
import org.identityconnectors.framework.common.objects.AttributeInfo;
import org.identityconnectors.framework.common.objects.AttributeInfoBuilder;
import org.identityconnectors.framework.common.objects.EmbeddedObject;

import java.util.HashMap;
import java.util.Map;

public abstract class AbstractAttributeBuilder<B extends AbstractAttributeBuilder<B,P>, P> implements AttributeBuilder<B, P> {

    BaseObjectClassDefinitionBuilder objectClass;
    AttributeInfoBuilder connIdBuilder = new AttributeInfoBuilder();

    Map<Class<? extends AttributeProtocolMapping<?,?>>, AttributeProtocolMappingBuilder> protocolMappings = new HashMap<>();


    DefinitionValue<Boolean> emulated = DefinitionValue.DEFAULT_FALSE;

    DefinitionValue<String> remoteName;
    String connIdName;
    Class<?> connIdType;

    private String complexType;

    protected AbstractAttributeBuilder(BaseObjectClassDefinitionBuilder restObjectClassBuilder, DefinitionValue<String> name) {
        this.remoteName = name;
        connIdBuilder.setName(name.value());
        connIdBuilder.setNativeName(name.value());
        this.objectClass = restObjectClassBuilder;
    }


    @Override
    @Deprecated
    public B protocolName(String protocolName) {
        json().name(protocolName);
        return self();
    }

    @Override
    @Deprecated
    public B remoteName(DefinitionValue<String> newName) {
        this.remoteName = this.remoteName.moreSpecific(newName);
        return self();
    }

    @Override
    public B emulated(DefinitionValue<Boolean> emulated) {
        this.emulated = this.emulated.moreSpecific(emulated);
        return self();
    }

    @Override
    public B readable(boolean readable) {
        connIdBuilder.setReadable(readable);
        if (!readable) {
            connIdBuilder.setReturnedByDefault(false);
        }
        return self();
    }


    @Override
    public B required(boolean required) {
        connIdBuilder.setRequired(required);
        return self();
    }

    @Override
    public B updatable(boolean updatable) {
        connIdBuilder.setUpdateable(updatable);
        return self();
    }

    @Override
    public B creatable(boolean creatable) {
        connIdBuilder.setCreateable(creatable);
        return self();
    }

    @Override
    public B description(String description) {
        connIdBuilder.setDescription(description);
        return self();
    }


    @Override
    public B complexType(String objectClass) {
        this.complexType = objectClass;
        connId().type(EmbeddedObject.class);
        connIdBuilder.setRoleInReference(AttributeInfo.RoleInReference.SUBJECT.toString());
        connIdBuilder.setReferencedObjectClassName(objectClass);
        json().implementation(new EmbeddedObjectJsonMapping(contextLookup(), objectClass));
        return self();
    }

    protected ContextLookup contextLookup() {
        return objectClass.contextLookup();
    }


    @Override
    public B returnedByDefault(boolean returnedByDefault) {
        connIdBuilder.setReturnedByDefault(returnedByDefault);
        return self();
    }

    @Override
    public B multiValued(boolean multiValued) {
        connIdBuilder.setMultiValued(multiValued);
        return self();
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
