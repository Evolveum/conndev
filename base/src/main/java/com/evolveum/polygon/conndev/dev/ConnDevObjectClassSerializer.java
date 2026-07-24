/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.dev;

import org.identityconnectors.framework.common.objects.Attribute;
import org.identityconnectors.framework.common.objects.AttributeBuilder;
import org.identityconnectors.framework.common.objects.ConnectorObject;
import org.identityconnectors.framework.common.objects.ConnectorObjectReference;
import org.identityconnectors.framework.common.objects.EmbeddedObject;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * The one shared serializer of the framework schema model into {@code conndev_ObjectClass}
 * {@link ConnectorObject}s (ticket #11455 "single source of default schema mapping").
 * <p>
 * Every connector builds its framework schema (conndev {@code BaseSchema} or an equivalent
 * implementing {@link ConnDevObjectClassSource}) from its raw source exactly once; both the ConnId
 * schema for provisioning and this development-mode export are then derived from that single model.
 * Connectors must not map their raw protocol schema to {@code conndev_ObjectClass} directly.
 */
public final class ConnDevObjectClassSerializer {

    private static final String CONN_ID_BLOCK = "connId";
    private static final String F_TYPE = "type";
    private static final String F_REQUIRED = "required";
    private static final String F_MULTI_VALUED = "multiValued";
    private static final String F_CREATABLE = "creatable";
    private static final String F_UPDATEABLE = "updateable";
    private static final String F_READABLE = "readable";
    private static final String F_RETURNED_BY_DEFAULT = "returnedByDefault";
    private static final String F_REFERENCED_OBJECT_CLASS = "referencedObjectClass";
    private static final String F_REFERENCED_ATTRIBUTE = "referencedAttribute";
    private static final String F_REFERENCE = "reference";
    private static final String F_ROLE = "role";

    private ConnDevObjectClassSerializer() {
    }

    /**
     * Serializes all application object classes of a schema. The {@code conndev_*} development
     * object classes themselves are skipped — they describe the export, they are not part of it.
     */
    public static List<ConnectorObject> serializeAll(Collection<? extends ConnDevObjectClassSource> objectClasses) {
        var result = new ArrayList<ConnectorObject>();
        for (var source : objectClasses) {
            if (source.connId().getType().startsWith("conndev_")) {
                continue;
            }
            result.add(serialize(source));
        }
        return result;
    }

    public static ConnectorObject serialize(ConnDevObjectClassSource source) {
        var name = source.connId().getType();
        var objectClass = ConnDevObjectClass.objectClass(name).uid(name);
        for (var attribute : source.attributes()) {
            serialize(attribute, objectClass.attribute(attribute.remoteName()));
        }
        source.contribute(objectClass);
        return objectClass.build();
    }

    private static void serialize(ConnDevAttributeSource source, ConnDevAttribute target) {
        var info = source.connId();
        var connId = new ArrayList<Attribute>();
        var type = source.nativeType() != null ? source.nativeType() : typeName(info.getType());
        if (type != null) {
            connId.add(AttributeBuilder.build(F_TYPE, type));
        }
        // Sparse emission: ConnId flag defaults (readable, creatable, updateable, returned by default,
        // single-valued, optional) are left implicit; only deviations are emitted.
        if (info.isRequired()) {
            connId.add(AttributeBuilder.build(F_REQUIRED, true));
        }
        if (info.isMultiValued()) {
            connId.add(AttributeBuilder.build(F_MULTI_VALUED, true));
        }
        if (!info.isCreateable()) {
            connId.add(AttributeBuilder.build(F_CREATABLE, false));
        }
        if (!info.isUpdateable()) {
            connId.add(AttributeBuilder.build(F_UPDATEABLE, false));
        }
        if (!info.isReadable()) {
            connId.add(AttributeBuilder.build(F_READABLE, false));
        }
        if (!info.isReturnedByDefault()) {
            connId.add(AttributeBuilder.build(F_RETURNED_BY_DEFAULT, false));
        }
        if (info.getReferencedObjectClassName() != null) {
            connId.add(AttributeBuilder.build(F_REFERENCED_OBJECT_CLASS, info.getReferencedObjectClassName()));
            if (info.getRoleInReference() != null) {
                connId.add(AttributeBuilder.build(F_ROLE, role(info.getRoleInReference())));
            }
            if (info.isReference() && info.getSubtype() != null) {
                connId.add(AttributeBuilder.build(F_REFERENCE, info.getSubtype()));
            }
            if (source.referencedAttribute() != null) {
                connId.add(AttributeBuilder.build(F_REFERENCED_ATTRIBUTE, source.referencedAttribute()));
            }
        }
        target.protocolSpecific(CONN_ID_BLOCK, connId);
        source.contribute(target);
    }

    /** ConnId stores the role as a special name ({@code __SUBJECT__}); export it as plain {@code subject}. */
    private static String role(String roleInReference) {
        return roleInReference.replace("__", "").toLowerCase();
    }

    private static String typeName(Class<?> type) {
        if (type == null) {
            return null;
        }
        if (ConnectorObjectReference.class.equals(type)) {
            return "reference";
        }
        if (EmbeddedObject.class.equals(type)) {
            return "complex";
        }
        if (byte[].class.equals(type)) {
            return "binary";
        }
        return type.getSimpleName().toLowerCase();
    }
}
