/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.schema;

import org.identityconnectors.framework.common.objects.ConnectorObjectBuilder;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.ObjectClassInfo;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class BaseObjectClassDefinition {

    private final Map<String, BaseAttributeDefinition> nativeAttributes;
    private final Map<String, BaseAttributeDefinition> connIdAttributes;
    ObjectClass clazz;
    ObjectClassInfo connId;

    Map<String, BaseAttributeDefinition> attributes = new HashMap<>();

    public BaseObjectClassDefinition(ObjectClassInfo connId, Map<String, BaseAttributeDefinition> nativeAttrs, Map<String, BaseAttributeDefinition> connIdAttrs) {
        this.connId = connId;
        this.clazz = new ObjectClass(connId.getType());
        this.nativeAttributes = nativeAttrs;
        this.connIdAttributes = connIdAttrs;
    }

    public ConnectorObjectBuilder newObjectBuilder() {
        ConnectorObjectBuilder builder = new ConnectorObjectBuilder();
        builder.setObjectClass(clazz);
        return builder;
    }

    public Collection<BaseAttributeDefinition> attributes() {
        return nativeAttributes.values();
    }

    public ObjectClassInfo connId() {
        return connId;
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
