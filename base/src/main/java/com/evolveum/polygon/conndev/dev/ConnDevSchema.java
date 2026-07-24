/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.dev;

import org.identityconnectors.framework.common.objects.*;

import java.util.List;

import static com.evolveum.polygon.conndev.dev.ConnDevObjectClass.*;

/**
 * Declares the development-mode object classes ({@code conndev_ObjectClass} and the embedded
 * {@code conndev_Attribute}) as plain ConnId {@link ObjectClassInfo}s, so a connector can add them to
 * its {@code schema()} in development mode and midPoint knows how to read the {@link ConnDevObjectClass}
 * objects. References between object classes are expressed on attributes (see {@code referencedObjectClass}
 * / {@code reference}). Independent of any framework-specific schema model.
 */
public final class ConnDevSchema {

    private ConnDevSchema() {
    }

    public static List<ObjectClassInfo> objectClassInfos() {
        return List.of(objectClass(), attribute());
    }

    private static ObjectClassInfo objectClass() {
        var builder = new ObjectClassInfoBuilder();
        builder.setType(OBJECT_CLASS_NAME);
        builder.addAttributeInfo(embedded("attributes", ATTRIBUTE_NAME));
        return builder.build();
    }

    private static ObjectClassInfo attribute() {
        var builder = new ObjectClassInfoBuilder();
        builder.setType(ATTRIBUTE_NAME);
        builder.setEmbedded(true);
        builder.addAttributeInfo(string(F_NAME));
        return builder.build();
    }

    private static AttributeInfo string(String name) {
        return AttributeInfoBuilder.build(name, String.class);
    }

    private static AttributeInfo embedded(String name, String objectClass) {
        var builder = new AttributeInfoBuilder(name, EmbeddedObject.class);
        builder.setMultiValued(true);
        builder.setReferencedObjectClassName(objectClass);
        // The holding object class (e.g. conndev_ObjectClass) owns the embedded child, so it is the
        // subject of the reference. midPoint requires the role for every referenced/embedded attribute.
        builder.setRoleInReference(AttributeInfo.RoleInReference.SUBJECT.toString());
        return builder.build();
    }
}
