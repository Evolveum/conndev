/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.dev;

import org.identityconnectors.framework.common.objects.AttributeInfo;
import org.identityconnectors.framework.common.objects.AttributeUtil;
import org.identityconnectors.framework.common.objects.ConnectorObject;
import org.identityconnectors.framework.common.objects.EmbeddedObject;
import org.identityconnectors.framework.common.objects.ObjectClassInfo;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

/**
 * Verifies the shared dev-mode builder: a connector feeds rich data, the builder emits the uniform
 * {@code conndev_ObjectClass} {@link ConnectorObject}; and the matching schema declaration.
 */
public class ConnDevObjectClassTest {

    @Test
    public void buildsNormalizedConnectorObject() {
        var objectClass = ConnDevObjectClass.objectClass("User")
                .uid("urn:User").endpoint("/Users").namespace("urn:User");
        objectClass.attribute("userName").type("string").required(true);
        objectClass.attribute("name").type("complex").subAttribute("familyName").type("string");
        objectClass.attribute("groups").type("reference").referencedObjectClass("Group")
                .referencedAttribute("id").reference("memberOf").role("subject").multiValued(true);

        ConnectorObject result = objectClass.build();

        assertEquals(result.getObjectClass().getObjectClassValue(), "conndev_ObjectClass");
        assertEquals(result.getUid().getUidValue(), "urn:User");
        assertEquals(result.getName().getNameValue(), "User");
        assertEquals(value(result, "endpoint"), "/Users");
        assertEquals(value(result, "namespace"), "urn:User");

        var attributes = result.getAttributeByName("attributes").getValue();
        var names = attributes.stream().map(e -> string((EmbeddedObject) e, "name")).collect(Collectors.toSet());
        assertEquals(names, Set.of("userName", "name", "groups"));

        var userName = embedded(attributes, "userName");
        assertEquals(string(userName, "type"), "string");
        assertEquals(single(userName, "required"), Boolean.TRUE);

        var name = embedded(attributes, "name");
        var subAttributes = AttributeUtil.find("subAttributes", name.getAttributes()).getValue();
        assertEquals(subAttributes.size(), 1);
        assertEquals(string((EmbeddedObject) subAttributes.get(0), "name"), "familyName");

        var groups = embedded(attributes, "groups");
        assertEquals(string(groups, "referencedObjectClass"), "Group");
        assertEquals(string(groups, "referencedAttribute"), "id");
        assertEquals(string(groups, "reference"), "memberOf");
        assertEquals(string(groups, "role"), "subject");
        assertEquals(single(groups, "multiValued"), Boolean.TRUE);
    }

    @Test
    public void emitsOnlyExplicitlySetProperties() {
        var objectClass = ConnDevObjectClass.objectClass("Bare").uid("Bare");
        objectClass.attribute("plain").type("string");

        var result = objectClass.build();

        // object-class level: endpoint/table/namespace were never set, so they are not emitted
        assertNull(result.getAttributeByName("endpoint"));
        assertNull(result.getAttributeByName("table"));
        assertNull(result.getAttributeByName("namespace"));

        // attribute level: only name + type were set, nothing else leaks in
        var plain = embedded(result.getAttributeByName("attributes").getValue(), "plain");
        assertEquals(string(plain, "type"), "string");
        assertNull(AttributeUtil.find("required", plain.getAttributes()));
        assertNull(AttributeUtil.find("multiValued", plain.getAttributes()));
        assertNull(AttributeUtil.find("creatable", plain.getAttributes()));
        assertNull(AttributeUtil.find("referencedObjectClass", plain.getAttributes()));
        assertNull(AttributeUtil.find("role", plain.getAttributes()));
        assertNull(AttributeUtil.find("subAttributes", plain.getAttributes()));
    }

    @Test
    public void buildsTableBackedObjectClass() {
        var objectClass = ConnDevObjectClass.objectClass("users")
                .uid("users").table("users").namespace("public");
        objectClass.attribute("id").type("integer");

        var result = objectClass.build();

        // table is the SQL locator; endpoint stays absent (the two are mutually exclusive)
        assertEquals(value(result, "table"), "users");
        assertNull(result.getAttributeByName("endpoint"));
        assertEquals(value(result, "namespace"), "public");
    }

    @Test
    public void declaresDevObjectClassesForSchema() {
        var names = ConnDevSchema.objectClassInfos().stream()
                .map(ObjectClassInfo::getType).collect(Collectors.toSet());
        assertEquals(names, Set.of("conndev_ObjectClass", "conndev_Attribute"));
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

    private static String value(ConnectorObject object, String name) {
        var attribute = object.getAttributeByName(name);
        return attribute == null ? null : AttributeUtil.getStringValue(attribute);
    }

    private static EmbeddedObject embedded(List<Object> values, String name) {
        return values.stream().map(EmbeddedObject.class::cast)
                .filter(e -> name.equals(string(e, "name")))
                .findFirst().orElseThrow();
    }

    private static String string(EmbeddedObject object, String name) {
        var attribute = AttributeUtil.find(name, object.getAttributes());
        return attribute == null ? null : AttributeUtil.getStringValue(attribute);
    }

    private static Object single(EmbeddedObject object, String name) {
        return AttributeUtil.getSingleValue(AttributeUtil.find(name, object.getAttributes()));
    }
}
