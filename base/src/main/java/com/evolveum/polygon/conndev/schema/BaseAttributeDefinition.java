/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.schema;

import com.evolveum.polygon.conndev.concepts.DefinitionValue;
import com.evolveum.polygon.conndev.json.JsonAttributeMapping;
import com.evolveum.polygon.conndev.spi.AttributeProtocolMapping;
import com.evolveum.polygon.conndev.spi.AttributeResolver;
import org.identityconnectors.framework.common.objects.Attribute;
import org.identityconnectors.framework.common.objects.AttributeBuilder;
import org.identityconnectors.framework.common.objects.AttributeInfo;
import org.identityconnectors.framework.common.objects.ConnectorObjectReference;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BaseAttributeDefinition {

    private final AttributeInfo info;
    private final DefinitionValue<String> remoteName;
    private final Map<Class<? extends AttributeProtocolMapping<?,?>>, AttributeProtocolMapping<?,?>> protocolMappings = new HashMap<>();
    private final DefinitionValue<Boolean> emulated;
    private AttributeResolver resolver;

    public BaseAttributeDefinition(BaseAttributeBuilder<?,?> mappedBuilder) {
        remoteName = mappedBuilder.remoteName;
        emulated = mappedBuilder.emulated;

        Class<?> suggestedConnIdType = null;
        for (var proto : mappedBuilder.protocolMappings.entrySet()) {
            var protocolMapping = proto.getValue().build();
            if (protocolMapping == null) {
                continue;
            }
            protocolMappings.put(proto.getKey(), protocolMapping);
            if (protocolMapping.connIdType() != null) {
                if (suggestedConnIdType != null && !protocolMapping.connIdType().equals(suggestedConnIdType)) {
                    throw new IllegalStateException("Multiple ConnID types declared for attribute. " + protocolMapping.connIdType() + ", " + suggestedConnIdType);
                }
                suggestedConnIdType = protocolMapping.connIdType();
            }
        }
        if (suggestedConnIdType == null) {
            suggestedConnIdType = mappedBuilder.connIdBuilder.type().value();
        }

        if (!mappedBuilder.isReference()) {
            if (suggestedConnIdType == null) {
                throw new IllegalArgumentException("Missing ConnId type definition for attribute " + remoteName);
            }
            mappedBuilder.connIdBuilder.type(DefinitionValue.detected(suggestedConnIdType));
        } else {
            mappedBuilder.connIdBuilder.type(DefinitionValue.detected(ConnectorObjectReference.class));
        }
        // FIXME: Do the reference attribute mappings

        info = mappedBuilder.connIdBuilder.build();
        mappedBuilder.deffered.set(this);

        if (mappedBuilder.resolverBuilder != null) {
            this.resolver = mappedBuilder.resolverBuilder.build();
        }
    }

    public String remoteName() {
        return this.remoteName.value();
    }

    public Attribute attributeOf(Object connIdValues) {
        if (connIdValues instanceof List) {
            return AttributeBuilder.build(info.getName(), (List<Object>) connIdValues);
        }
        return AttributeBuilder.build(info.getName(), connIdValues);
    }

    public AttributeInfo connId() {
        return this.info;
    }

    public JsonAttributeMapping json() {
        return mapping(JsonAttributeMapping.class);
    }

    public <T extends AttributeProtocolMapping<?,?>> T mapping(Class<T> type) {
        return type.cast(protocolMappings.get(type));
    }

    public boolean emulated() {
        return emulated.value();
    }

    public AttributeResolver resolver() {
        return resolver;
    }

    @Override
    public String toString() {
        return new StringBuilder(getClass().getSimpleName())
                .append("{connid=")
                .append(info.getName())
                .append(", remoteName=")
                .append(remoteName)
                .append('}')
                .toString();
    }
}
