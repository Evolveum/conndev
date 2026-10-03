/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.schema;

import com.evolveum.polygon.conndev.api.ContextLookup;
import com.evolveum.polygon.conndev.build.ConnIdBuiltInAttribute;
import com.evolveum.polygon.conndev.concepts.DefinitionValue;
import com.evolveum.polygon.conndev.concepts.SourceLocation;
import org.identityconnectors.framework.common.objects.Name;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.Uid;
import org.testng.annotations.Test;
import tools.jackson.databind.node.JsonNodeFactory;

import static org.testng.Assert.*;

/**
 * WP #12419: an object whose identifier doubles as its name maps the same protocol
 * attribute to both {@code __UID__} and {@code __NAME__}. The attribute keeps
 * {@code __UID__} (in any declaration order) and {@code __NAME__} is satisfied by the
 * {@code NameDefaultsToUidRule} derivation, so the declaration no longer fails with
 * "Multiple declarations for the same definition detected"; any other conflicting
 * pair of declared ConnId names still fails.
 */
public class UidNameSharedAttributeTest {

    private static TestSchemaBuilder schema;

    private static final class TestSchemaBuilder extends BaseSchemaBuilder<
            TestSchemaBuilder,
            TestObjectClass,
            TestSchemaBuilder,
            TestObjectClass,
            BaseObjectClassDefinition<BaseAttributeDefinition>,
            BaseSchema<BaseObjectClassDefinition<BaseAttributeDefinition>>> {

        TestSchemaBuilder() {
            super(StubConnector.class, ContextLookup.none());
        }

        @Override
        protected TestObjectClass newObjectClass(DefinitionValue<ObjectClass> name) {
            return new TestObjectClass(this, name);
        }
    }

    private static TestObjectClass newObjectClass() {
        schema = new TestSchemaBuilder();
        var objectClass = schema.objectClass(DefinitionValue.from(new ObjectClass("Test"), SourceLocation.capture()));
        var idAttribute = objectClass.attribute("id");
        idAttribute.json().type("integer");
        return objectClass;
    }

    private static void applyStructuralRules() {
        schema.applyStructuralRules();
    }

    private static String connIdNameOf(TestObjectClass objectClass, String attributeName) {
        return objectClass.attribute(attributeName).connId().name().value();
    }

    @Test
    public void sharedAttributeUidDeclaredFirstKeepsUidAndDerivesName() {
        var objectClass = newObjectClass();
        objectClass.connIdAttribute("UID", "id");
        objectClass.connIdAttribute("NAME", "id");

        // the attribute keeps __UID__; nothing claims __NAME__ before the structural rules
        assertEquals(connIdNameOf(objectClass, "id"), Uid.NAME);
        assertTrue(objectClass.connIdAttributeNotDefined(Name.NAME));

        applyStructuralRules();

        assertEquals(connIdNameOf(objectClass, "id"), Uid.NAME, "__UID__ must keep the attribute");
        var nameAttribute = objectClass.attributeBuilderFromConnIdName(Name.NAME);
        assertNotNull(nameAttribute, "default __NAME__ attribute should be created");
        assertTrue(nameAttribute.derivedFromUid().value(), "default __NAME__ should be marked derivedFromUid");
        var definition = nameAttribute.build();
        assertEquals(definition.connId().getName(), Name.NAME);
        assertEquals(definition.connId().getType(), String.class, "__NAME__ must be String-typed");
        // the derived NAME reads the same wire field as the UID, not a literal __NAME__ field
        var mapping = definition.json();
        assertEquals(mapping.path().onlyAttribute().name(), "id");
        var sample = JsonNodeFactory.instance.objectNode().set("id", JsonNodeFactory.instance.numberNode(42));
        assertEquals(mapping.singleValueFromAttribute(mapping.attributeFromObject(sample)), "42");
    }

    @Test
    public void sharedAttributeNameDeclaredFirstStillEndsUpAsUid() {
        var objectClass = newObjectClass();
        objectClass.connIdAttribute("NAME", "id");
        objectClass.connIdAttribute("UID", "id");

        // __UID__ wins the slot regardless of declaration order
        assertEquals(connIdNameOf(objectClass, "id"), Uid.NAME);
        assertTrue(objectClass.connIdAttributeNotDefined(Name.NAME));

        applyStructuralRules();

        assertEquals(connIdNameOf(objectClass, "id"), Uid.NAME);
        var nameAttribute = objectClass.attributeBuilderFromConnIdName(Name.NAME);
        assertNotNull(nameAttribute, "default __NAME__ attribute should be created");
        assertTrue(nameAttribute.derivedFromUid().value());
        var mapping = nameAttribute.build().json();
        assertEquals(mapping.path().onlyAttribute().name(), "id");
    }

    @Test
    public void sharedAttributeViaAttributeLevelConnIdNames() {
        var objectClass = newObjectClass();
        var idAttribute = objectClass.attribute("id");
        idAttribute.connId().name(ConnIdBuiltInAttribute.UID);
        idAttribute.connId().name(ConnIdBuiltInAttribute.NAME);

        assertEquals(idAttribute.connId().name().value(), Uid.NAME, "__UID__ must win the ConnId slot");

        applyStructuralRules();

        var nameAttribute = objectClass.attributeBuilderFromConnIdName(Name.NAME);
        assertNotNull(nameAttribute, "default __NAME__ attribute should be created");
        assertTrue(nameAttribute.derivedFromUid().value());
    }

    @Test
    public void repeatedSameBuiltInClaimIsNoOp() {
        var objectClass = newObjectClass();
        objectClass.connIdAttribute("UID", "id");
        objectClass.connIdAttribute("UID", "id");

        applyStructuralRules();

        assertEquals(connIdNameOf(objectClass, "id"), Uid.NAME);
        assertNotNull(objectClass.attributeBuilderFromConnIdName(Name.NAME),
                "default __NAME__ should still be derived");
    }

    @Test
    public void conflictingNonBuiltInNameStillFails() {
        var objectClass = newObjectClass();
        var displayAttribute = objectClass.attribute("displayName");
        displayAttribute.json().type("string");
        displayAttribute.connId().name("displayName");

        var exception = expectThrows(IllegalArgumentException.class,
                () -> displayAttribute.connId().name(Uid.NAME));
        assertTrue(exception.getMessage().contains("Multiple declarations"),
                "only the UID/NAME pair is legal on one attribute, got: " + exception.getMessage());
    }

    @Test
    public void explicitNameOnOtherAttributeStillSuppressesDefault() {
        var objectClass = newObjectClass();
        var loginAttribute = objectClass.attribute("login");
        loginAttribute.json().type("string");
        objectClass.connIdAttribute("UID", "id");
        objectClass.connIdAttribute("NAME", "login");
        objectClass.connIdAttribute("NAME", "id");

        applyStructuralRules();

        // the explicit NAME claim on another attribute wins over the shared-attribute claim
        assertNotNull(objectClass.attributeBuilderFromConnIdName(Name.NAME), "explicit __NAME__ should exist");
        assertEquals(connIdNameOf(objectClass, "id"), Uid.NAME);
        assertEquals(connIdNameOf(objectClass, "login"), Name.NAME);
    }
}
