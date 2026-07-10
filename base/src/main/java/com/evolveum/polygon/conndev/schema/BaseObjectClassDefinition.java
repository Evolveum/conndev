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

/**
 * Object-class definition for the ConnDev framework.
 *
 * <p>This class encapsulates all metadata needed to describe an object class within a connector
 * schema. It holds the ConnId {@link ObjectClassInfo}, the ConnId {@link ObjectClass} for runtime
 * use, native-side identifier maps, and the remote-system locator and namespace (e.g. SCIM schema
 * URN, SQL table name).</p>
 *
 * <p>Implements {@link ConnDevObjectClassSource} to expose a read-only view of the object class
 * to the ConnDev development-mode export pipeline.</p>
 */
public class BaseObjectClassDefinition<A extends BaseAttributeDefinition> implements ConnDevObjectClassSource {

    /** Native (remote-system) attribute definitions, keyed by their protocol name. */
    private final Map<String, A> nativeAttributes;

    /** ConnId-side attribute definitions, keyed by ConnId attribute name. */
    private final Map<String, A> connIdAttributes;

    /** The ConnId {@link ObjectClass} instance derived from the {@link #connId} info. */
    ObjectClass clazz;

    /** The ConnId {@link ObjectClassInfo} describing this object class. */
    ObjectClassInfo connId;

    /**
     * Where the object class lives in the remote system: the resource endpoint for REST/SCIM,
     * the table for SQL. Set after construction by {@link #locator(String)}.
     */
    private String locator;

    /** Namespace of the object class (SCIM schema URN, SQL schema name); set by {@link #namespace(String)}. */
    private String namespace;

    /** Full attribute map keyed by attribute name, combining both native and ConnId attributes. */
    Map<String, A> attributes = new HashMap<>();

    /**
     * Constructs a new object-class definition with the given ConnId information and attribute maps.
     *
     * <p>Creates the {@link #clazz} field from the type of the supplied {@code connId} info,
     * and stores the provided native and ConnId attribute maps for lookup by protocol or ConnId name.</p>
     *
     * @param connId the ConnId {@link ObjectClassInfo} for this object class
     * @param nativeAttrs map of native (remote-system) attribute definitions keyed by attribute name
     * @param connIdAttrs map of ConnId-side attribute definitions keyed by ConnId attribute name
     */
    public BaseObjectClassDefinition(ObjectClassInfo connId, Map<String, A> nativeAttrs, Map<String, A> connIdAttrs) {
        this.connId = connId;
        this.clazz = new ObjectClass(connId.getType());
        this.nativeAttributes = nativeAttrs;
        this.connIdAttributes = connIdAttrs;
    }

    /**
     * Creates a new {@link ConnectorObjectBuilder} pre-configured with this object class.
     *
     * @return a fresh connector object builder backed by this object class
     */
    public ConnectorObjectBuilder newObjectBuilder() {
        var builder = new ConnectorObjectBuilder();
        builder.setObjectClass(clazz);
        return builder;
    }

    /**
     * Returns the native (remote-system) attribute definitions of this object class.
     *
     * <p>Implements {@link ConnDevObjectClassSource#attributes()} so the development-mode
     * export can read the schema directly from this model.</p>
     *
     * @return an unmodifiable collection of all native attribute definitions
     */
    @Override
    public Collection<A> attributes() {
        return nativeAttributes.values();
    }

    /**
     * Returns the ConnId {@link ObjectClassInfo} for this object class.
     *
     * <p>Implements {@link ConnDevObjectClassSource#connId()}.</p>
     *
     * @return the ConnId object class information
     */
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

    /**
     * Returns the ConnId {@link ObjectClass} instance for this object class.
     *
     * <p>This is derived from the {@link #connId} info and used throughout the ConnId
     * framework for operation identification.</p>
     *
     * @return the object class value
     */
    public ObjectClass objectClass() {
        return clazz;
    }

    /**
     * Resolves an attribute definition by its native (protocol) name.
     *
     * <p>The native name is the identifier used by the remote system's protocol
     * (e.g. JSON property name for REST, column name for SQL).</p>
     *
     * @param protocolName the native/remote attribute name
     * @return the matching {@link BaseAttributeDefinition}, or {@code null} if not found
     */
    public BaseAttributeDefinition attributeFromProtocolName(String protocolName) {
        return nativeAttributes.get(protocolName);
    }

    /**
     * Returns the name of this object class.
     *
     * <p>Returns the object class value string from {@link #objectClass()}, which
     * corresponds to the ConnId type name.</p>
     *
     * @return the object class value string
     */
    public String name() {
        return objectClass().getObjectClassValue();
    }

    /**
     * Resolves an attribute definition by its ConnId attribute name.
     *
     * <p>The ConnId name is the identifier used by the ConnId framework, which may differ
     * from the native name due to mapping (e.g. a sanitized variant of a remote property).</p>
     *
     * @param name the ConnId attribute name
     * @return the matching {@link BaseAttributeDefinition}, or {@code null} if not found
     */
    public A attributeFromConnIdName(String name) {
        return connIdAttributes.get(name);
    }
}