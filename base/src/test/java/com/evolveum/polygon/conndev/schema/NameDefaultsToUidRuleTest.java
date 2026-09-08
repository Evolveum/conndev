/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.schema;

import com.evolveum.polygon.conndev.api.ContextLookup;
import com.evolveum.polygon.conndev.concepts.DefinitionValue;
import com.evolveum.polygon.conndev.concepts.SourceLocation;
import org.identityconnectors.framework.common.objects.Name;
import org.identityconnectors.framework.common.objects.Uid;
import org.testng.annotations.Test;
import tools.jackson.databind.node.JsonNodeFactory;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

/**
 * Verifies the object-class-level "NAME defaults to a copy of UID" structural rule
 * ({@code NameDefaultsToUidRule}): when an object class defines {@code __UID__} but nothing
 * claims {@code __NAME__}, a {@code __NAME__} attribute is created as a copy of the UID
 * attribute's protocol mapping and marked {@code derivedFromUid}; explicit NAME mappings and
 * UID-less object classes are left alone.
 */
public class NameDefaultsToUidRuleTest {

    private static TestSchemaBuilder schema;

    /** Schema builder that materializes {@link TestObjectClass}es so the structural
     * rules (applied on the whole builder) reach the test's object classes. */
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
        protected TestObjectClass newObjectClass(DefinitionValue<String> name) {
            return new TestObjectClass(this, name);
        }
    }

    private static TestObjectClass newObjectClass() {
        schema = new TestSchemaBuilder();
        return schema.objectClass(DefinitionValue.from("Test", SourceLocation.capture()));
    }

    private static TestObjectClass newObjectClassWithUid() {
        var objectClass = newObjectClass();
        var idAttribute = objectClass.attribute("id");
        idAttribute.connId().name(Uid.NAME);
        idAttribute.json().type("integer");
        return objectClass;
    }

    private static void applyStructuralRules() {
        schema.applyStructuralRules();
    }

    private static TestAttributeBuilder nameAttribute(TestObjectClass objectClass) {
        return objectClass.attributeBuilderFromConnIdName(Name.NAME);
    }

    @Test
    public void nameIsCreatedAsCopyOfUidWhenNotDefined() {
        var objectClass = newObjectClassWithUid();
        assertTrue(objectClass.needsDefaultNameFromUid());

        applyStructuralRules();

        var nameAttribute = nameAttribute(objectClass);
        assertNotNull(nameAttribute, "default __NAME__ attribute should be created");
        assertTrue(nameAttribute.derivedFromUid().value(), "default __NAME__ should be marked derivedFromUid");
        var definition = nameAttribute.build();
        assertEquals(definition.connId().getName(), Name.NAME);
        assertEquals(definition.connId().getType(), String.class, "__NAME__ must be String-typed");
    }

    @Test
    public void nameCopyInheritsUidProtocolMapping() {
        var objectClass = newObjectClassWithUid();

        applyStructuralRules();

        var nameAttribute = nameAttribute(objectClass);
        var mapping = nameAttribute.build().json();
        assertNotNull(mapping, "default __NAME__ should carry a JSON mapping");
        // copy of the UID's integer JSON type, coerced to String on the ConnId side
        assertEquals(mapping.connIdType(), String.class);
        Object value = mapping.singleValueFromAttribute(JsonNodeFactory.instance.numberNode(42));
        assertEquals(value, "42");
    }

    @Test
    public void explicitNameSuppressesTheDefault() {
        var objectClass = newObjectClass();
        var idAttribute = objectClass.attribute("id");
        idAttribute.connId().name(Uid.NAME);
        idAttribute.json().type("integer");
        var loginAttribute = objectClass.attribute("login");
        loginAttribute.connId().name(Name.NAME);
        loginAttribute.json().type("string");
        assertFalse(objectClass.needsDefaultNameFromUid());

        applyStructuralRules();

        // exactly one __NAME__ attribute — the explicit one, not a derived copy
        var nameAttribute = nameAttribute(objectClass);
        assertNotNull(nameAttribute);
        assertFalse(nameAttribute.derivedFromUid().value(), "explicit __NAME__ must not be marked derivedFromUid");
        assertEquals(nameAttribute.build().json().connIdType(), String.class);
    }

    @Test
    public void uidLessObjectClassGetsNoDefaultName() {
        var objectClass = newObjectClass();
        var countAttribute = objectClass.attribute("count");
        countAttribute.json().type("integer");
        assertFalse(objectClass.needsDefaultNameFromUid());

        applyStructuralRules();

        assertNull(nameAttribute(objectClass), "no __NAME__ without a __UID__ attribute");
    }

    @Test
    public void uidWithoutProtocolMappingGetsNoDefaultName() {
        var objectClass = newObjectClass();
        var idAttribute = objectClass.attribute("id");
        idAttribute.connId().name(Uid.NAME);
        // no json() mapping for the UID — the base hook has nothing to copy
        assertTrue(objectClass.needsDefaultNameFromUid());

        applyStructuralRules();

        assertNull(nameAttribute(objectClass), "no __NAME__ when the UID has no protocol mapping to copy");
    }

    @Test
    public void stringWireUidCopyNeedsNoCoercion() {
        var objectClass = newObjectClass();
        var idAttribute = objectClass.attribute("id");
        idAttribute.connId().name(Uid.NAME);
        idAttribute.json().type("string");

        applyStructuralRules();

        var nameAttribute = nameAttribute(objectClass);
        var mapping = nameAttribute.build().json();
        assertEquals(mapping.connIdType(), String.class);
        assertEquals(mapping.singleValueFromAttribute(JsonNodeFactory.instance.stringNode("abc")), "abc");
    }
}
