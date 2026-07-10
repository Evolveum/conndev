/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.schema;

import com.evolveum.polygon.conndev.concepts.DefinitionValue;
import com.evolveum.polygon.conndev.dev.ConnDevAttributeSource;
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

/**
 * Represents a single attribute definition within a ConnDev connector schema.
 *
 * <p>This class encapsulates all metadata needed to describe an attribute: its ConnId
 * {@link AttributeInfo}, the remote/system-side identifier ({@code remoteName}), any
 * protocol-specific mappings (e.g. JSON path mappings), whether the attribute should
 * be treated as emulated, and an optional {@link AttributeResolver} for runtime value
 * resolution.</p>
 *
 * <p>Instance construction performs type resolution across all protocol mappings and
 * the ConnId builder, producing a consistent {@link AttributeInfo} ready for use by
 * the ConnId framework.</p>
 */
public class BaseAttributeDefinition implements ConnDevAttributeSource {

    /** The ConnId {@link AttributeInfo} describing attributes name, type, multi-valued-ness, etc. */
    private final AttributeInfo info;

    /** The remote (target system) name for this attribute, as a lazily-resolved value. */
    private final DefinitionValue<String> remoteName;

    /** Protocol-specific mappings (keyed by mapping class) that describe how the attribute appears in various protocols. */
    private final Map<Class<? extends AttributeProtocolMapping<?,?>>, AttributeProtocolMapping<?,?>> protocolMappings = new HashMap<>();

    /** Whether this attribute is emulated (i.e. simulated rather than directly present on the target). */
    private final DefinitionValue<Boolean> emulated;

    /** Optional resolver for computing or transforming attribute values at runtime. */
    private AttributeResolver resolver;

    /**
     * Constructs a {@code BaseAttributeDefinition} from the supplied builder, performing type
     * resolution across protocol mappings and building the final {@link AttributeInfo}.
     *
     * @param builder the {@code BaseAttributeBuilder} providing all metadata for this attribute
     * @throws IllegalStateException if multiple protocol mappings declare conflicting ConnId types
     * @throws IllegalArgumentException if no ConnId type can be resolved for a non-reference attribute
     */
    public BaseAttributeDefinition(BaseAttributeBuilder<?,?,?,?> builder) {
        remoteName = builder.remoteName;
        emulated = builder.emulated;

        Class<?> suggestedConnIdType = null;
        for (var proto : builder.protocolMappings.entrySet()) {
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
            suggestedConnIdType = builder.connIdBuilder.type().value();
        }

        if (!builder.isReference()) {
            if (suggestedConnIdType == null) {
                throw new IllegalArgumentException("Missing ConnId type definition for attribute " + remoteName);
            }
            builder.connIdBuilder.type(DefinitionValue.detected(suggestedConnIdType));
        } else {
            builder.connIdBuilder.type(DefinitionValue.detected(ConnectorObjectReference.class));
        }

        info = builder.connIdBuilder.build();
        builder.deffered.set(this);

        if (builder.resolverBuilder != null) {
            this.resolver = builder.resolverBuilder.build();
        }
    }

    /**
     * Returns the remote (target-system) name for this attribute, resolving the
     * {@link DefinitionValue} at call time.
     *
     * @return the remote name as a {@code String}
     */
    public String remoteName() {
        return this.remoteName.value();
    }

    /**
     * Builds a ConnId {@link Attribute} from the given ConnId-side value(s), using
     * the {@code info} (attribute name) contained in this definition.
     *
     * <p>If the supplied value is a {@link List}, a multi-valued attribute is created.
     * Otherwise, a single-valued attribute is produced.</p>
     *
     * @param connIdValues the value(s) to place in the attribute
     * @return a new {@link Attribute} instance
     */
    public Attribute attributeOf(Object connIdValues) {
        if (connIdValues instanceof List) {
            return AttributeBuilder.build(info.getName(), (List<Object>) connIdValues);
        }
        return AttributeBuilder.build(info.getName(), connIdValues);
    }

    /**
     * Returns the ConnId {@link AttributeInfo} for this attribute, describing
     * its name, type, multi-valued status, and other ConnId-level metadata.
     *
     * @return the read-only {@link AttributeInfo}
     */
    public AttributeInfo connId() {
        return this.info;
    }

    /**
     * Returns the JSON protocol mapping for this attribute, if one is defined.
     *
     * @return the {@link JsonAttributeMapping}, or {@code null} if not configured
     */
    public JsonAttributeMapping json() {
        return mapping(JsonAttributeMapping.class);
    }

    /**
     * Returns the protocol mapping for the given mapping type, if one has been registered.
     *
     * @param type the {@link AttributeProtocolMapping} class (e.g. {@link JsonAttributeMapping})
     * @param <T>  the concrete mapping type
     * @return the matching mapping instance, or {@code null} if not configured
     */
    public <T extends AttributeProtocolMapping<?,?>> T mapping(Class<T> type) {
        return type.cast(protocolMappings.get(type));
    }

    /**
     * Indicates whether this attribute is emulated (i.e. simulated rather than
     * natively present on the target system).
     *
     * @return {@code true} if the attribute is emulated
     */
    public boolean emulated() {
        return emulated.value();
    }

    /**
     * Returns the {@link AttributeResolver} associated with this attribute, if one has been
     * configured via the builder.
     *
     * @return the resolver, or {@code null} if no resolver was defined
     */
    public AttributeResolver resolver() {
        return resolver;
    }

    /**
     * Returns a human-readable string summarizing this attribute definition,
     * including the ConnId attribute name and the remote name.
     *
     * @return a string such as {@code BaseAttributeDefinition{connid=foo, remoteName=bar}}
     */
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
