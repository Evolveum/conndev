/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.dev;

import org.identityconnectors.framework.common.objects.*;

import java.util.Collection;
import java.util.List;

import static com.evolveum.polygon.conndev.dev.ConnDevObjectClass.*;

/**
 * Declares the development-mode object classes ({@code conndev_ObjectClass} and the embedded
 * {@code conndev_Attribute}, plus the shared {@code connId} block) as plain ConnId
 * {@link ObjectClassInfo}s, so a connector can add them to its {@code schema()} in development mode
 * and midPoint knows how to read the {@link ConnDevObjectClass} objects. References between object
 * classes are expressed on attributes (see {@code referencedObjectClass} / {@code reference}).
 * Independent of any framework-specific schema model.
 * <p>
 * Protocol-specific blocks (e.g. {@code scim}, {@code sql}) are not declared here - conndev does not
 * know their fields. A connector contributing such a block via {@link ConnDevObjectClassSource#contribute}
 * / {@link ConnDevAttributeSource#contribute} must also declare its shape, passing the extra attribute
 * declarations to {@link #objectClassInfos(Collection, Collection)} (built with {@link #embeddedBlock})
 * and registering the referenced nested {@link ObjectClassInfo} itself.
 */
public final class ConnDevSchema {

    static final String CONN_ID_BLOCK_TYPE =
            ConnDevAttribute.attributeProtocolBlockType(ConnDevObjectClassSerializer.CONN_ID_BLOCK);

    private ConnDevSchema() {
    }

    public static List<ObjectClassInfo> objectClassInfos() {
        return objectClassInfos(List.of(), List.of());
    }

    public static List<ObjectClassInfo> objectClassInfos(
            Collection<AttributeInfo> extraObjectClassFields, Collection<AttributeInfo> extraAttributeFields) {
        var objectClass = new ObjectClassInfoBuilder();
        objectClass.setType(OBJECT_CLASS_NAME);
        objectClass.addAttributeInfo(attributesList());
        extraObjectClassFields.forEach(objectClass::addAttributeInfo);

        var attribute = new ObjectClassInfoBuilder();
        attribute.setType(ATTRIBUTE_NAME);
        attribute.setEmbedded(true);
        attribute.addAttributeInfo(string(F_NAME));
        attribute.addAttributeInfo(embeddedBlock(ConnDevObjectClassSerializer.CONN_ID_BLOCK, CONN_ID_BLOCK_TYPE));
        extraAttributeFields.forEach(attribute::addAttributeInfo);

        return List.of(objectClass.build(), attribute.build(), connIdBlock());
    }

    private static ObjectClassInfo connIdBlock() {
        var builder = new ObjectClassInfoBuilder();
        builder.setType(CONN_ID_BLOCK_TYPE);
        builder.setEmbedded(true);
        builder.addAttributeInfo(string(ConnDevObjectClassSerializer.F_TYPE));
        builder.addAttributeInfo(bool(ConnDevObjectClassSerializer.F_REQUIRED));
        builder.addAttributeInfo(bool(ConnDevObjectClassSerializer.F_MULTI_VALUED));
        builder.addAttributeInfo(bool(ConnDevObjectClassSerializer.F_CREATABLE));
        builder.addAttributeInfo(bool(ConnDevObjectClassSerializer.F_UPDATEABLE));
        builder.addAttributeInfo(bool(ConnDevObjectClassSerializer.F_READABLE));
        builder.addAttributeInfo(bool(ConnDevObjectClassSerializer.F_RETURNED_BY_DEFAULT));
        builder.addAttributeInfo(string(ConnDevObjectClassSerializer.F_REFERENCED_OBJECT_CLASS));
        builder.addAttributeInfo(string(ConnDevObjectClassSerializer.F_REFERENCED_ATTRIBUTE));
        builder.addAttributeInfo(string(ConnDevObjectClassSerializer.F_REFERENCE));
        builder.addAttributeInfo(string(ConnDevObjectClassSerializer.F_ROLE));
        return builder.build();
    }

    private static AttributeInfo attributesList() {
        var builder = new AttributeInfoBuilder(F_ATTRIBUTES, EmbeddedObject.class);
        builder.setMultiValued(true);
        builder.setReferencedObjectClassName(ATTRIBUTE_NAME);
        // The holding object class (e.g. conndev_ObjectClass) owns the embedded child, so it is the
        // subject of the reference. midPoint requires the role for every referenced/embedded attribute.
        builder.setRoleInReference(AttributeInfo.RoleInReference.SUBJECT.toString());
        return builder.build();
    }

    /**
     * Declares a single-valued protocol-specific block attribute (e.g. {@code scim}, {@code sql}),
     * referencing its own nested object class. Connectors use this to declare their own blocks
     * alongside {@link #objectClassInfos(Collection, Collection)}, and must separately register that
     * nested {@link ObjectClassInfo} (via {@code SchemaBuilder.defineObjectClass}).
     */
    public static AttributeInfo embeddedBlock(String name, String objectClass) {
        var builder = new AttributeInfoBuilder(name, EmbeddedObject.class);
        builder.setReferencedObjectClassName(objectClass);
        builder.setRoleInReference(AttributeInfo.RoleInReference.SUBJECT.toString());
        return builder.build();
    }

    private static AttributeInfo string(String name) {
        return AttributeInfoBuilder.build(name, String.class);
    }

    private static AttributeInfo bool(String name) {
        return AttributeInfoBuilder.build(name, Boolean.class);
    }
}
