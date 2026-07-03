/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.schema;

import com.evolveum.polygon.conndev.dev.ConnDevObjectClassSource;
import org.identityconnectors.framework.common.objects.ConnectorObjectBuilder;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.ObjectClassInfo;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class BaseObjectClassDefinition implements ConnDevObjectClassSource {

    private final Map<String, BaseAttributeDefinition> nativeAttributes;
    private final Map<String, BaseAttributeDefinition> connIdAttributes;
    ObjectClass clazz;
    ObjectClassInfo connId;

    // Native-side object-class metadata: where the object class lives in the remote system
    // (endpoint for REST/SCIM, table for SQL) and its namespace (SCIM schema URN, SQL schema).
    private String locator;
    private String namespace;

    Map<String, BaseAttributeDefinition> attributes = new HashMap<>();

    public BaseObjectClassDefinition(ObjectClassInfo connId, Map<String, BaseAttributeDefinition> nativeAttrs, Map<String, BaseAttributeDefinition> connIdAttrs) {
        this.connId = connId;
        this.clazz = new ObjectClass(connId.getType());
        this.nativeAttributes = nativeAttrs;
        this.connIdAttributes = connIdAttrs;
    }

    public ConnectorObjectBuilder newObjectBuilder() {
        var builder = new ConnectorObjectBuilder();
        builder.setObjectClass(clazz);
        return builder;
    }

    @Override
    public Collection<BaseAttributeDefinition> attributes() {
        return nativeAttributes.values();
    }

    @Override
    public ObjectClassInfo connId() {
        return connId;
    }

    @Override
    public String locator() {
        return locator;
    }

    void locator(String locator) {
        this.locator = locator;
    }

    @Override
    public String namespace() {
        return namespace;
    }

    void namespace(String namespace) {
        this.namespace = namespace;
    }

    public ObjectClass objectClass() {
        return clazz;
    }

    public BaseAttributeDefinition attributeFromProtocolName(String protocolName) {
        return nativeAttributes.get(protocolName);
    }

    public String name() {
        return objectClass().getObjectClassValue();
    }

    public BaseAttributeDefinition attributeFromConnIdName(String name) {
        return connIdAttributes.get(name);
    }
}
