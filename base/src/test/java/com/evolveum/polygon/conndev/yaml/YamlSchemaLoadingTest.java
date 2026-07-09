/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.yaml;

import com.evolveum.polygon.conndev.api.ContextLookup;
import com.evolveum.polygon.conndev.groovy.GroovyContext;
import com.evolveum.polygon.conndev.groovy.GroovySchemaLoader;
import com.evolveum.polygon.conndev.schema.BaseSchema;
import com.evolveum.polygon.conndev.schema.BaseSchemaBuilder;
import org.identityconnectors.framework.common.objects.AttributeInfo;
import org.identityconnectors.framework.common.objects.ConnectorObjectReference;
import org.identityconnectors.framework.common.objects.Name;
import org.identityconnectors.framework.common.objects.Uid;
import org.identityconnectors.framework.spi.Configuration;
import org.identityconnectors.framework.spi.Connector;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertThrows;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

/**
 * Declarative YAML schema loading: the YAML front-end drives the same {@link BaseSchemaBuilder} as
 * the Groovy DSL, follows the two-file convention ({@code *.native.schema.yaml} +
 * {@code *.connid.schema.yaml} merging into one object class) and fails fast on invalid documents.
 */
public class YamlSchemaLoadingTest {

    private static final class StubConnector implements Connector {
        @Override public Configuration getConfiguration() { return null; }
        @Override public void init(Configuration c) { }
        @Override public void dispose() { }
    }

    private static final ContextLookup NOOP_CONTEXT = ContextLookup.none();

    private static BaseSchemaBuilder schemaBuilder() {
        return new BaseSchemaBuilder(StubConnector.class, NOOP_CONTEXT);
    }

    /** Loads the whole test connector definition: native + connid files for every object class. */
    private static BaseSchema loadTestSchema() {
        var loader = new YamlSchemaLoader(schemaBuilder());
        loader.loadFromResource("/yaml/User.native.schema.yaml");
        loader.loadFromResource("/yaml/User.connid.schema.yaml");
        loader.loadFromResource("/yaml/Group.native.schema.yaml");
        loader.loadFromResource("/yaml/Address.native.schema.yaml");
        return loader.build();
    }

    @Test
    public void nativeAndConnIdFilesMergeIntoOneObjectClass() {
        var user = loadTestSchema().objectClass("User");

        assertNotNull(user);
        // the ConnId overlay from User.connid.schema.yaml mapped the native attributes
        assertEquals(user.attributeFromConnIdName(Uid.NAME).remoteName(), "id");
        assertEquals(user.attributeFromConnIdName(Name.NAME).remoteName(), "login");
        // a UID-mapped attribute is forced to the ConnId String type
        assertEquals(user.attributeFromConnIdName(Uid.NAME).connId().getType(), String.class);
    }

    @Test
    public void attributeTypesAndFormatsAreApplied() {
        var user = loadTestSchema().objectClass("User");

        assertEquals(user.attributeFromProtocolName("admin").connId().getType(), Boolean.class);
        assertEquals(user.attributeFromProtocolName("email").connId().getType(), String.class);
        // the OpenAPI format drives the ConnId type, exactly like in the Groovy DSL
        assertEquals(user.attributeFromProtocolName("createdAt").connId().getType(), java.time.ZonedDateTime.class);
    }

    @Test
    public void attributeFlagsAreApplied() {
        var user = loadTestSchema().objectClass("User");

        var id = user.attributeFromProtocolName("id").connId();
        assertFalse(id.isCreateable());
        assertFalse(id.isUpdateable());

        var password = user.attributeFromProtocolName("password").connId();
        assertFalse(password.isReadable());
        assertFalse(password.isReturnedByDefault());

        var login = user.attributeFromProtocolName("login").connId();
        assertTrue(login.isCreateable());
        assertTrue(login.isUpdateable());
    }

    @Test
    public void referenceIsAppliedWithRoleAndSubtype() {
        var group = loadTestSchema().objectClass("Group");

        var members = group.attributeFromProtocolName("members").connId();
        assertEquals(members.getType(), ConnectorObjectReference.class);
        assertEquals(members.getReferencedObjectClassName(), "User");
        assertEquals(members.getSubtype(), "_User_Group_Membership");
        assertEquals(members.getRoleInReference(), AttributeInfo.RoleInReference.OBJECT.toString());
        assertTrue(members.isMultiValued());
    }

    @Test
    public void objectClassMetadataIsApplied() {
        var schema = loadTestSchema();

        var group = schema.objectClass("Group");
        assertEquals(group.locator(), "/Groups");
        assertEquals(group.namespace(), "urn:ietf:params:scim:schemas:core:2.0:Group");
        assertEquals(group.connId().getDescription(), "SCIM group");

        assertTrue(schema.objectClass("Address").connId().isEmbedded());
    }

    @Test
    public void groovyAndYamlDefinitionsCoexistOnOneBuilder() {
        var builder = schemaBuilder();
        new GroovySchemaLoader(new GroovyContext(), builder)
                .load("objectClass(\"FromGroovy\") { attribute(\"a\") { jsonType \"string\" } }");
        var yamlLoader = new YamlSchemaLoader(builder);
        yamlLoader.load("""
                objectClass: FromYaml
                attributes:
                  b:
                    jsonType: string
                """);

        var schema = yamlLoader.build();

        assertNotNull(schema.objectClass("FromGroovy"));
        assertNotNull(schema.objectClass("FromYaml"));
        assertEquals(schema.objectClass("FromYaml").attributeFromProtocolName("b").connId().getType(), String.class);
    }

    @Test
    public void unknownKeyFailsFast() {
        var exception = expectThrows(IllegalArgumentException.class, () -> new YamlSchemaLoader(schemaBuilder()).load("""
                objectClass: Broken
                attributes:
                  name:
                    creatabel: false
                """));

        assertTrue(exception.getMessage().contains("creatabel"),
                "error should name the unknown key: " + exception.getMessage());
    }

    @Test
    public void multiDocumentFileIsRejected() {
        var exception = expectThrows(IllegalArgumentException.class, () -> new YamlSchemaLoader(schemaBuilder()).load("""
                objectClass: One
                ---
                objectClass: Two
                """));

        assertTrue(exception.getMessage().contains("exactly one object class"),
                "error should explain the one-class-per-file rule: " + exception.getMessage());
    }

    @Test
    public void missingObjectClassNameFails() {
        assertThrows(IllegalArgumentException.class, () -> new YamlSchemaLoader(schemaBuilder()).load("""
                attributes:
                  a:
                    jsonType: string
                """));
    }

    @Test
    public void guardedStringConnIdTypeIsApplied() {
        var loader = new YamlSchemaLoader(schemaBuilder());
        // no jsonType: an explicit ConnId type without a JSON mapping, like in the Groovy DSL
        loader.load("""
                objectClass: Secure
                attributes:
                  password:
                    readable: false
                    connId:
                      type: guardedstring
                """);

        var password = loader.build().objectClass("Secure").attributeFromProtocolName("password").connId();

        assertEquals(password.getType(), org.identityconnectors.common.security.GuardedString.class);
        assertFalse(password.isReadable());
    }

    @Test
    public void unknownConnIdTypeFails() {
        var exception = expectThrows(IllegalArgumentException.class, () -> new YamlSchemaLoader(schemaBuilder()).load("""
                objectClass: Broken
                attributes:
                  a:
                    connId:
                      type: uuid
                """));

        assertTrue(exception.getMessage().contains("uuid"), exception.getMessage());
    }
}
