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
import com.evolveum.polygon.conndev.json.JsonAttributeMapping;
import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.framework.common.objects.AttributeBuilder;
import org.identityconnectors.framework.common.objects.Name;
import org.identityconnectors.framework.common.objects.OperationalAttributes;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.Uid;
import org.testng.annotations.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.testng.Assert.*;

/**
 * Verifies that special ConnId attributes (UID / NAME — forced {@link String},
 * and the password attributes — forced {@link GuardedString}) are always exposed
 * as their forced type on the ConnId side, even when the protocol (JSON) native
 * type differs — e.g. a schema whose native {@code id} attribute has an integer
 * wire type while being declared as the UID attribute, or a {@code String}-backed
 * JSON mapping behind a {@code __PASSWORD__} attribute.
 * <p>
 * Covers both halves of the mechanism, both applied externally via
 * {@link BaseSchemaBuilder#applyStructuralRules()} before building (the production
 * order — protocol detection first, structural rules second):
 * <ul>
 *   <li>{@code AttributeTypeResolutionRule} — the built-in forced type from
 *       {@code ConnIdBuiltInAttribute} wins over protocol suggestions and declared
 *       types, forcing the ConnId type to String,</li>
 *   <li>{@code AttributeTypeCoercionRule} — pushes that final type into the JSON
 *       mapping builder, so the {@link JsonAttributeMapping} produced by
 *       {@code json().type("integer")} reports {@code String} as its ConnId type
 *       and converts numeric wire values to/from String, while leaving the wire
 *       representation a JSON number.</li>
 * </ul>
 */
public class ForcedConnIdTypeTest {

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
        protected TestObjectClass newObjectClass(DefinitionValue<ObjectClass> name) {
            return new TestObjectClass(this, name);
        }
    }

    private static TestObjectClass newObjectClass() {
        schema = new TestSchemaBuilder();
        return schema.objectClass(DefinitionValue.from(new ObjectClass("Test"), SourceLocation.capture()));
    }

    private static TestAttributeBuilder newUidAttribute() {
        var attribute = newObjectClass().attribute("id");
        attribute.connId().name(Uid.NAME);
        return attribute;
    }

    private static TestAttributeBuilder newPasswordAttribute() {
        var attribute = newObjectClass().attribute("password");
        attribute.connId().name(OperationalAttributes.PASSWORD_NAME);
        return attribute;
    }

    /** Applies the structural rules (type resolution + type coercion) and then builds
     * the attribute — the same order the connectors use. */
    private static BaseAttributeDefinition build(TestAttributeBuilder attribute) {
        schema.applyStructuralRules();
        return attribute.build();
    }

    @Test
    public void uidAttribute_forcesString() {
        var attribute = newUidAttribute();
        attribute.json().type("integer");
        assertThat(build(attribute).connId().getType()).isEqualTo(String.class);
    }

    @Test
    public void nameAttribute_forcesString() {
        var attribute = newObjectClass().attribute("login");
        attribute.connId().name(Name.NAME);
        attribute.json().type("integer");

        assertThat(build(attribute).connId().getType()).isEqualTo(String.class);
    }


    @Test
    public void defaultType_doesNotSuppressForcing() {
        var attribute = newUidAttribute();
        attribute.json().type("integer");

        assertEquals(build(attribute).connId().getType(), String.class);
    }

    @Test
    public void detectedType_doesNotSuppressForcing() {
        var attribute = newUidAttribute();
        attribute.json().type("integer");
        attribute.connId().type(DefinitionValue.detected( String.class));
        assertEquals(build(attribute).connId().getType(), String.class);
    }

    @Test
    public void uidAttribute_integerWire_mappingReportsStringConnIdType() {
        var attribute = newUidAttribute();
        attribute.json().type("integer");

        var mapping = build(attribute).json();

        assertEquals(mapping.connIdType(), String.class);
    }

    @Test
    public void uidAttribute_integerWire_deserializesNumberAsString() {
        var attribute = newUidAttribute();
        attribute.json().type("integer");

        var mapping = build(attribute).json();
        Object value = mapping.singleValueFromAttribute(JsonNodeFactory.instance.numberNode(42));

        assertTrue(value instanceof String, "UID value should be deserialized as String, got: " + value);
        assertEquals(value, "42");
    }

    @Test
    public void uidAttribute_integerWire_serializesStringBackToNumber() {
        var attribute = newUidAttribute();
        attribute.json().type("integer");

        var mapping = build(attribute).json();
        ObjectNode parent = JsonNodeFactory.instance.objectNode();

        mapping.toJsonNode(AttributeBuilder.build(Uid.NAME, "42"), parent);

        JsonNode node = parent.get("id");
        assertNotNull(node);
        assertTrue(node.isIntegralNumber(), "Wire value should stay a JSON number, got: " + node);
        assertEquals(node.intValue(), 42);
    }

    @Test
    public void nameAttribute_integerWire_forcedToString() {
        var attribute = newObjectClass().attribute("login");
        attribute.connId().name(Name.NAME);
        attribute.json().type("integer");

        var mapping = build(attribute).json();

        assertEquals(mapping.connIdType(), String.class);
        assertEquals(mapping.singleValueFromAttribute(JsonNodeFactory.instance.numberNode(7)), "7");
    }

    @Test
    public void uidAttribute_int64Wire_forcedToString() {
        var attribute = newUidAttribute();
        attribute.json().type("integer").openApiFormat("int64");

        var mapping = build(attribute).json();

        assertEquals(mapping.connIdType(), String.class);
        assertEquals(mapping.singleValueFromAttribute(JsonNodeFactory.instance.numberNode(42)), "42");
    }

    @Test
    public void uidAttribute_stringWire_noOverrideNeeded() {
        var attribute = newUidAttribute();
        attribute.json().type("string");

        var mapping = build(attribute).json();

        assertEquals(mapping.connIdType(), String.class);
        assertEquals(mapping.singleValueFromAttribute(JsonNodeFactory.instance.stringNode("abc")), "abc");
    }

    @Test
    public void regularAttribute_integerWire_keepsNativeType() {
        var attribute = newObjectClass().attribute("count");
        attribute.json().type("integer");

        var mapping = build(attribute).json();

        assertEquals(mapping.connIdType(), Integer.class);
        assertEquals(mapping.singleValueFromAttribute(JsonNodeFactory.instance.numberNode(42)), 42);
    }

    @Test
    public void passwordAttribute_forcesGuardedString() {
        var attribute = newPasswordAttribute();
        attribute.json().type("string");

        assertThat(build(attribute).connId().getType()).isEqualTo(GuardedString.class);
    }

    @Test
    public void passwordAttribute_stringWire_mappingReportsGuardedStringConnIdType() {
        var attribute = newPasswordAttribute();
        attribute.json().type("string");

        var mapping = build(attribute).json();

        assertEquals(mapping.connIdType(), GuardedString.class);
    }

    @Test
    public void passwordAttribute_stringWire_deserializesStringAsGuardedString() {
        var attribute = newPasswordAttribute();
        attribute.json().type("string");

        var mapping = build(attribute).json();
        Object value = mapping.singleValueFromAttribute(JsonNodeFactory.instance.stringNode("secret"));

        assertTrue(value instanceof GuardedString, "password value should be deserialized as GuardedString, got: " + value);
        assertEquals(value, new GuardedString("secret".toCharArray()));
    }

    @Test
    public void passwordAttribute_passwordFormatWire_deserializesStringAsGuardedString() {
        var attribute = newPasswordAttribute();
        attribute.json().type("string").openApiFormat("password");

        var mapping = build(attribute).json();

        assertEquals(mapping.connIdType(), GuardedString.class);
        assertEquals(mapping.singleValueFromAttribute(JsonNodeFactory.instance.stringNode("secret")),
                new GuardedString("secret".toCharArray()));
    }

    @Test
    public void passwordAttribute_nullWire_deserializesAsNull() {
        var attribute = newPasswordAttribute();
        attribute.json().type("string");

        var mapping = build(attribute).json();

        assertNull(mapping.singleValueFromAttribute(JsonNodeFactory.instance.nullNode()));
    }

    @Test
    public void passwordAttribute_stringWire_serializesGuardedStringBackToString() {
        var attribute = newPasswordAttribute();
        attribute.json().type("string");

        var mapping = build(attribute).json();
        ObjectNode parent = JsonNodeFactory.instance.objectNode();

        mapping.toJsonNode(
                AttributeBuilder.build(OperationalAttributes.PASSWORD_NAME, new GuardedString("secret".toCharArray())),
                parent);

        JsonNode node = parent.get("password");
        assertNotNull(node);
        assertTrue(node.isTextual(), "Wire value should be a JSON string, got: " + node);
        assertEquals(node.asText(), "secret");
    }

}