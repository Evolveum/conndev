/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.dev;

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
        if (source.locator() != null) {
            objectClass.locator(source.locator());
        }
        if (source.namespace() != null) {
            objectClass.namespace(source.namespace());
        }
        for (var attribute : source.attributes()) {
            serialize(attribute, objectClass.attribute(attribute.remoteName()));
        }
        return objectClass.build();
    }

    private static void serialize(ConnDevAttributeSource source, ConnDevAttribute target) {
        var info = source.connId();
        var type = source.nativeType() != null ? source.nativeType() : typeName(info.getType());
        if (type != null) {
            target.type(type);
        }
        // Sparse emission: ConnId flag defaults (readable, creatable, updateable, returned by default,
        // single-valued, optional) are left implicit; only deviations are emitted.
        if (info.isRequired()) {
            target.required(true);
        }
        if (info.isMultiValued()) {
            target.multiValued(true);
        }
        if (!info.isCreateable()) {
            target.creatable(false);
        }
        if (!info.isUpdateable()) {
            target.updateable(false);
        }
        if (!info.isReadable()) {
            target.readable(false);
        }
        if (!info.isReturnedByDefault()) {
            target.returnedByDefault(false);
        }
        if (info.getReferencedObjectClassName() != null) {
            target.referencedObjectClass(info.getReferencedObjectClassName());
            if (info.getRoleInReference() != null) {
                target.role(role(info.getRoleInReference()));
            }
            if (info.isReference() && info.getSubtype() != null) {
                target.reference(info.getSubtype());
            }
            if (source.referencedAttribute() != null) {
                target.referencedAttribute(source.referencedAttribute());
            }
        }
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
