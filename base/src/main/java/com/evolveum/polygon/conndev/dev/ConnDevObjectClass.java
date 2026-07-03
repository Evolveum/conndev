/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.dev;

import org.identityconnectors.framework.common.objects.AttributeBuilder;
import org.identityconnectors.framework.common.objects.ConnectorObject;
import org.identityconnectors.framework.common.objects.ConnectorObjectBuilder;
import org.identityconnectors.framework.common.objects.EmbeddedObject;
import org.identityconnectors.framework.common.objects.ObjectClass;

import java.util.ArrayList;
import java.util.List;

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
    static final String F_TYPE = "type";
    static final String F_REQUIRED = "required";
    static final String F_MULTI_VALUED = "multiValued";
    static final String F_CREATABLE = "creatable";
    static final String F_UPDATEABLE = "updateable";
    static final String F_READABLE = "readable";
    static final String F_RETURNED_BY_DEFAULT = "returnedByDefault";
    static final String F_SUB_ATTRIBUTES = "subAttributes";
    static final String F_REFERENCED_OBJECT_CLASS = "referencedObjectClass";
    static final String F_REFERENCED_ATTRIBUTE = "referencedAttribute";
    static final String F_REFERENCE = "reference";
    static final String F_ROLE = "role";
    static final String F_ENDPOINT = "endpoint";
    static final String F_TABLE = "table";
    static final String F_NAMESPACE = "namespace";

    private final String name;
    private String uid;
    private String endpoint;
    private String table;
    private String namespace;
    private final List<ConnDevAttribute> attributes = new ArrayList<>();

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

    /** REST/SCIM locator (e.g. the resource endpoint path). Not used by SQL — see {@link #table(String)}. */
    public ConnDevObjectClass endpoint(String endpoint) {
        this.endpoint = endpoint;
        return this;
    }

    /** SQL locator: the target table. Not used by REST/SCIM — see {@link #endpoint(String)}. */
    public ConnDevObjectClass table(String table) {
        this.table = table;
        return this;
    }

    public ConnDevObjectClass namespace(String namespace) {
        this.namespace = namespace;
        return this;
    }

    public ConnDevAttribute attribute(String name) {
        var attribute = new ConnDevAttribute(name);
        attributes.add(attribute);
        return attribute;
    }

    public ConnectorObject build() {
        var builder = new ConnectorObjectBuilder();
        builder.setObjectClass(OBJECT_CLASS);
        builder.setUid(uid);
        builder.setName(name);
        if (endpoint != null) {
            builder.addAttribute(AttributeBuilder.build(F_ENDPOINT, endpoint));
        }
        if (table != null) {
            builder.addAttribute(AttributeBuilder.build(F_TABLE, table));
        }
        if (namespace != null) {
            builder.addAttribute(AttributeBuilder.build(F_NAMESPACE, namespace));
        }
        var attributeObjects = new ArrayList<EmbeddedObject>();
        for (var attribute : attributes) {
            attributeObjects.add(attribute.build());
        }
        builder.addAttribute(AttributeBuilder.build(F_ATTRIBUTES, attributeObjects));
        return builder.build();
    }
}
