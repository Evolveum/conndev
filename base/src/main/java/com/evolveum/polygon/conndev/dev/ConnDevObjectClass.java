/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.dev;

import org.identityconnectors.framework.common.objects.*;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Fluent builder for the normalized development-mode schema representation.
 * <p>
 * Each connector reads its own <em>rich</em> source (SCIM scim2-sdk-client objects, SQL JDBC
 * metadata, ...) and uses this builder to emit a uniform {@code conndev_ObjectClass}
 * {@link ConnectorObject}; midPoint then reads all of them the same way. Shared across SCIM, SQL and
 * any other connector — the source differs per connector, the produced ConnectorObject shape is
 * always the same. The connector-specific mapper decides the content; this builder owns the shape.
 */
public final class ConnDevObjectClass {

    public static final String OBJECT_CLASS_NAME = "conndev_ObjectClass";
    public static final String ATTRIBUTE_NAME = "conndev_Attribute";

    static final ObjectClass OBJECT_CLASS = new ObjectClass(OBJECT_CLASS_NAME);
    static final ObjectClass ATTRIBUTE = new ObjectClass(ATTRIBUTE_NAME);

    static final String F_ATTRIBUTES = "attributes";
    static final String F_NAME = "name";

    private final String name;
    private String uid;
    private final List<ConnDevAttribute> attributes = new ArrayList<>();
    private final Map<String, Collection<Attribute>> protocolSpecifics = new LinkedHashMap<>();

    private ConnDevObjectClass(String name) {
        this.name = name;
    }

    public static ConnDevObjectClass objectClass(String name) {
        return new ConnDevObjectClass(name);
    }

    public ConnDevObjectClass uid(String uid) {
        this.uid = uid;
        return this;
    }

    public ConnDevAttribute attribute(String name) {
        var attribute = new ConnDevAttribute(name);
        attributes.add(attribute);
        return attribute;
    }

    /**
     * Adds a named, protocol-specific block (e.g. {@code "scim"}, {@code "sql"}), exported as its own
     * nested {@link EmbeddedObject}. This builder and {@link ConnDevObjectClassSerializer} stay
     * protocol-agnostic - the caller decides the protocol name and the attributes it carries.
     */
    public ConnDevObjectClass protocolSpecific(String protocolName, Collection<Attribute> attributes) {
        protocolSpecifics.put(protocolName, attributes);
        return this;
    }

    public ConnectorObject build() {
        var builder = new ConnectorObjectBuilder();
        builder.setObjectClass(OBJECT_CLASS);
        builder.setUid(uid);
        builder.setName(name);
        for (var entry : protocolSpecifics.entrySet()) {
            var block = new EmbeddedObject(new ObjectClass(entry.getKey()), Set.copyOf(entry.getValue()));
            builder.addAttribute(AttributeBuilder.build(entry.getKey(), block));
        }
        var attributeObjects = new ArrayList<EmbeddedObject>();
        for (var attribute : attributes) {
            attributeObjects.add(attribute.build());
        }
        builder.addAttribute(AttributeBuilder.build(F_ATTRIBUTES, attributeObjects));
        return builder.build();
    }
}
