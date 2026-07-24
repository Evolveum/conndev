/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.dev;

import org.identityconnectors.framework.common.objects.Attribute;
import org.identityconnectors.framework.common.objects.AttributeBuilder;
import org.identityconnectors.framework.common.objects.EmbeddedObject;
import org.identityconnectors.framework.common.objects.ObjectClass;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static com.evolveum.polygon.conndev.dev.ConnDevObjectClass.ATTRIBUTE;
import static com.evolveum.polygon.conndev.dev.ConnDevObjectClass.F_NAME;

/**
 * Fluent builder for a {@code conndev_Attribute} embedded object. Carries only a {@code name} and
 * named, protocol-specific blocks (e.g. {@code "connId"}, {@code "scim"}, {@code "sql"}) - the
 * per-connector mapper decides exactly what each block carries.
 */
public final class ConnDevAttribute {

    private final String name;
    private final Map<String, Collection<Attribute>> protocolSpecifics = new LinkedHashMap<>();

    ConnDevAttribute(String name) {
        this.name = name;
    }

    /**
     * Adds a named, protocol-specific block (e.g. {@code "connId"}, {@code "scim"}, {@code "sql"}),
     * exported as its own nested {@link EmbeddedObject}. This builder and
     * {@link ConnDevObjectClassSerializer} stay protocol-agnostic - the caller decides the protocol
     * name and the attributes it carries.
     */
    public ConnDevAttribute protocolSpecific(String protocolName, Collection<Attribute> attributes) {
        protocolSpecifics.put(protocolName, attributes);
        return this;
    }

    EmbeddedObject build() {
        Set<Attribute> properties = new HashSet<>();
        properties.add(AttributeBuilder.build(F_NAME, name));
        for (var entry : protocolSpecifics.entrySet()) {
            var block = new EmbeddedObject(new ObjectClass(entry.getKey()), Set.copyOf(entry.getValue()));
            properties.add(AttributeBuilder.build(entry.getKey(), block));
        }
        return new EmbeddedObject(ATTRIBUTE, properties);
    }
}
