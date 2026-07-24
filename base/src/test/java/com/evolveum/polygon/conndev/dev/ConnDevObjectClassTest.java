/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.dev;

import org.identityconnectors.framework.common.objects.*;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.testng.Assert.*;

/**
 * Verifies the shared dev-mode builder: a connector feeds rich data, the builder emits the uniform
 * {@code conndev_ObjectClass} {@link ConnectorObject}; and the matching schema declaration.
 */
public class ConnDevObjectClassTest {

    @Test
    public void buildsNormalizedConnectorObject() {
        var objectClass = ConnDevObjectClass.objectClass("User").uid("urn:User");
        objectClass.protocolSpecific("scim", List.of(
                AttributeBuilder.build("name", "User"), AttributeBuilder.build("schemaUri", "urn:User")));
        objectClass.attribute("userName").protocolSpecific("connId", List.of(
                AttributeBuilder.build("type", "string"), AttributeBuilder.build("required", true)));
        objectClass.attribute("groups").protocolSpecific("connId", List.of(
                AttributeBuilder.build("type", "reference"),
                AttributeBuilder.build("referencedObjectClass", "Group"),
                AttributeBuilder.build("referencedAttribute", "id"),
                AttributeBuilder.build("reference", "memberOf"),
                AttributeBuilder.build("role", "subject"),
                AttributeBuilder.build("multiValued", true)));

        ConnectorObject result = objectClass.build();

        assertEquals(result.getObjectClass().getObjectClassValue(), "conndev_ObjectClass");
        assertEquals(result.getUid().getUidValue(), "urn:User");
        assertEquals(result.getName().getNameValue(), "User");
        var scim = protocolBlock(result, "scim");
        assertEquals(string(scim, "name"), "User");
        assertEquals(string(scim, "schemaUri"), "urn:User");

        var attributes = result.getAttributeByName("attributes").getValue();
        var names = attributes.stream().map(e -> string((EmbeddedObject) e, "name")).collect(Collectors.toSet());
        assertEquals(names, Set.of("userName", "groups"));

        var userName = protocolBlock(embedded(attributes, "userName"), "connId");
        assertEquals(string(userName, "type"), "string");
        assertEquals(single(userName, "required"), Boolean.TRUE);

        var groups = protocolBlock(embedded(attributes, "groups"), "connId");
        assertEquals(string(groups, "referencedObjectClass"), "Group");
        assertEquals(string(groups, "referencedAttribute"), "id");
        assertEquals(string(groups, "reference"), "memberOf");
        assertEquals(string(groups, "role"), "subject");
        assertEquals(single(groups, "multiValued"), Boolean.TRUE);
    }

    @Test
    public void emitsOnlyExplicitlySetProperties() {
        var objectClass = ConnDevObjectClass.objectClass("Bare").uid("Bare");
        objectClass.attribute("plain").protocolSpecific("connId", List.of(AttributeBuilder.build("type", "string")));

        var result = objectClass.build();

        // object-class level: no protocol-specific block was ever added, so none is emitted
        assertNull(result.getAttributeByName("scim"));
        assertNull(result.getAttributeByName("sql"));

        // attribute level: only the explicitly given connId fields are set, nothing else leaks in
        var plain = embedded(result.getAttributeByName("attributes").getValue(), "plain");
        var connId = protocolBlock(plain, "connId");
        assertEquals(string(connId, "type"), "string");
        assertNull(AttributeUtil.find("required", connId.getAttributes()));
        assertNull(AttributeUtil.find("multiValued", connId.getAttributes()));
        assertNull(AttributeUtil.find("creatable", connId.getAttributes()));
        assertNull(AttributeUtil.find("referencedObjectClass", connId.getAttributes()));
        assertNull(AttributeUtil.find("role", connId.getAttributes()));
    }

    @Test
    public void buildsTableBackedObjectClass() {
        var objectClass = ConnDevObjectClass.objectClass("users").uid("users");
        objectClass.protocolSpecific("sql", List.of(
                AttributeBuilder.build("table", "users"), AttributeBuilder.build("schema", "public")));
        objectClass.attribute("id").protocolSpecific("connId", List.of(AttributeBuilder.build("type", "integer")));

        var result = objectClass.build();

        var sql = protocolBlock(result, "sql");
        assertEquals(string(sql, "table"), "users");
        assertEquals(string(sql, "schema"), "public");
    }

    @Test
    public void declaresDevObjectClassesForSchema() {
        var names = ConnDevSchema.objectClassInfos().stream()
                .map(ObjectClassInfo::getType).collect(Collectors.toSet());
        assertEquals(names, Set.of("conndev_ObjectClass", "conndev_Attribute", "conndev_connIdAttribute"));
    }

    @Test
    public void declaresSubjectRoleForEmbeddedAttributes() {
        var objectClass = objectClassInfo("conndev_ObjectClass");
        var attributes = attributeInfo(objectClass, "attributes");

        // Embedded references require a role; the holder is the subject (else midPoint rejects it
        // with "Missing role in reference").
        assertTrue(attributes.isMultiValued());
        assertEquals(attributes.getReferencedObjectClassName(), "conndev_Attribute");
        assertEquals(attributes.getRoleInReference(), AttributeInfo.RoleInReference.SUBJECT.toString());
    }

    private static ObjectClassInfo objectClassInfo(String type) {
        return ConnDevSchema.objectClassInfos().stream()
                .filter(info -> type.equals(info.getType()))
                .findFirst().orElseThrow();
    }

    private static AttributeInfo attributeInfo(ObjectClassInfo objectClass, String name) {
        return objectClass.getAttributeInfo().stream()
                .filter(info -> name.equals(info.getName()))
                .findFirst().orElseThrow();
    }

    private static EmbeddedObject embedded(List<Object> values, String name) {
        return values.stream().map(EmbeddedObject.class::cast)
                .filter(e -> name.equals(string(e, "name")))
                .findFirst().orElseThrow();
    }

    private static EmbeddedObject protocolBlock(ConnectorObject object, String protocolName) {
        return (EmbeddedObject) AttributeUtil.getSingleValue(object.getAttributeByName(protocolName));
    }

    private static EmbeddedObject protocolBlock(EmbeddedObject attribute, String protocolName) {
        var block = AttributeUtil.find(protocolName, attribute.getAttributes());
        return (EmbeddedObject) AttributeUtil.getSingleValue(block);
    }

    private static String string(EmbeddedObject object, String name) {
        var attribute = AttributeUtil.find(name, object.getAttributes());
        return attribute == null ? null : AttributeUtil.getStringValue(attribute);
    }

    private static Object single(EmbeddedObject object, String name) {
        return AttributeUtil.getSingleValue(AttributeUtil.find(name, object.getAttributes()));
    }
}
