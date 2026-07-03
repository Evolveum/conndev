/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.dev;

import org.identityconnectors.framework.common.objects.AttributeInfo;
import org.identityconnectors.framework.common.objects.AttributeInfoBuilder;
import org.identityconnectors.framework.common.objects.EmbeddedObject;
import org.identityconnectors.framework.common.objects.ObjectClassInfo;
import org.identityconnectors.framework.common.objects.ObjectClassInfoBuilder;

import java.util.List;

import static com.evolveum.polygon.conndev.dev.ConnDevObjectClass.ATTRIBUTE_NAME;
import static com.evolveum.polygon.conndev.dev.ConnDevObjectClass.F_CREATABLE;
import static com.evolveum.polygon.conndev.dev.ConnDevObjectClass.F_ENDPOINT;
import static com.evolveum.polygon.conndev.dev.ConnDevObjectClass.F_MULTI_VALUED;
import static com.evolveum.polygon.conndev.dev.ConnDevObjectClass.F_NAME;
import static com.evolveum.polygon.conndev.dev.ConnDevObjectClass.F_NAMESPACE;
import static com.evolveum.polygon.conndev.dev.ConnDevObjectClass.F_READABLE;
import static com.evolveum.polygon.conndev.dev.ConnDevObjectClass.F_REFERENCE;
import static com.evolveum.polygon.conndev.dev.ConnDevObjectClass.F_REFERENCED_ATTRIBUTE;
import static com.evolveum.polygon.conndev.dev.ConnDevObjectClass.F_REFERENCED_OBJECT_CLASS;
import static com.evolveum.polygon.conndev.dev.ConnDevObjectClass.F_REQUIRED;
import static com.evolveum.polygon.conndev.dev.ConnDevObjectClass.F_RETURNED_BY_DEFAULT;
import static com.evolveum.polygon.conndev.dev.ConnDevObjectClass.F_ROLE;
import static com.evolveum.polygon.conndev.dev.ConnDevObjectClass.F_SUB_ATTRIBUTES;
import static com.evolveum.polygon.conndev.dev.ConnDevObjectClass.F_TABLE;
import static com.evolveum.polygon.conndev.dev.ConnDevObjectClass.F_TYPE;
import static com.evolveum.polygon.conndev.dev.ConnDevObjectClass.OBJECT_CLASS_NAME;

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
        builder.addAttributeInfo(string(F_ENDPOINT));
        builder.addAttributeInfo(string(F_TABLE));
        builder.addAttributeInfo(string(F_NAMESPACE));
        builder.addAttributeInfo(embedded("attributes", ATTRIBUTE_NAME));
        return builder.build();
    }

    private static ObjectClassInfo attribute() {
        var builder = new ObjectClassInfoBuilder();
        builder.setType(ATTRIBUTE_NAME);
        builder.setEmbedded(true);
        builder.addAttributeInfo(string(F_NAME));
        builder.addAttributeInfo(string(F_TYPE));
        builder.addAttributeInfo(string(F_NAMESPACE));
        // A reference (SCIM reference attribute / SQL foreign key) is expressed on the attribute itself.
        builder.addAttributeInfo(string(F_REFERENCED_OBJECT_CLASS));
        builder.addAttributeInfo(string(F_REFERENCED_ATTRIBUTE));
        builder.addAttributeInfo(string(F_REFERENCE));
        builder.addAttributeInfo(string(F_ROLE));
        builder.addAttributeInfo(bool(F_REQUIRED));
        builder.addAttributeInfo(bool(F_MULTI_VALUED));
        builder.addAttributeInfo(bool(F_CREATABLE));
        builder.addAttributeInfo(bool("updateable"));
        builder.addAttributeInfo(bool(F_READABLE));
        builder.addAttributeInfo(bool(F_RETURNED_BY_DEFAULT));
        builder.addAttributeInfo(embedded(F_SUB_ATTRIBUTES, ATTRIBUTE_NAME));
        return builder.build();
    }

    private static AttributeInfo string(String name) {
        return AttributeInfoBuilder.build(name, String.class);
    }

    private static AttributeInfo bool(String name) {
        return AttributeInfoBuilder.build(name, Boolean.class);
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
