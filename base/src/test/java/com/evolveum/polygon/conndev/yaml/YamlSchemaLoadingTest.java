/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.yaml;

import com.evolveum.polygon.conndev.api.*;
import com.evolveum.polygon.conndev.concepts.DefinitionValue;
import com.evolveum.polygon.conndev.groovy.GroovyContext;
import com.evolveum.polygon.conndev.groovy.GroovySchemaLoader;
import com.evolveum.polygon.conndev.schema.BaseObjectClassDefinitionBuilder;
import com.evolveum.polygon.conndev.schema.BaseSchema;
import com.evolveum.polygon.conndev.schema.BaseSchemaBuilder;
import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.framework.common.objects.AttributeInfo;
import org.identityconnectors.framework.common.objects.ConnectorObjectReference;
import org.identityconnectors.framework.common.objects.Name;
import org.identityconnectors.framework.common.objects.Uid;
import org.identityconnectors.framework.spi.Configuration;
import org.identityconnectors.framework.spi.Connector;
import org.testng.annotations.Test;
import tools.jackson.databind.JsonNode;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

import static org.testng.Assert.*;

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
        var builder = schemaBuilder();
        var loader = new YamlSchemaLoader(builder);
        loader.loadFromResource("/yaml/User.native.schema.yaml");
        loader.loadFromResource("/yaml/User.connid.schema.yaml");
        loader.loadFromResource("/yaml/Group.native.schema.yaml");
        loader.loadFromResource("/yaml/Address.native.schema.yaml");
        builder.applyStructuralRules();
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
        assertEquals(user.attributeFromProtocolName("createdAt").connId().getType(), ZonedDateTime.class);
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
                objectClasses:
                  FromYaml:
                    attributes:
                      b:
                        jsonType: string
                """);

        builder.applyStructuralRules();
        var schema = yamlLoader.build();

        assertNotNull(schema.objectClass("FromGroovy"));
        assertNotNull(schema.objectClass("FromYaml"));
        assertEquals(schema.objectClass("FromYaml").attributeFromProtocolName("b").connId().getType(), String.class);
    }

    @Test
    public void unknownKeyFailsFast() {
        var exception = expectThrows(IllegalArgumentException.class, () -> new YamlSchemaLoader(schemaBuilder()).load("""
                objectClasses:
                  Broken:
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
                objectClasses:
                  One: {}
                ---
                objectClasses:
                  Two: {}
                """));

        assertTrue(exception.getMessage().contains("exactly one document"),
                "error should explain the one-document-per-file rule: " + exception.getMessage());
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
        var builder = schemaBuilder();
        var loader = new YamlSchemaLoader(builder);
        // no jsonType: an explicit ConnId type without a JSON mapping, like in the Groovy DSL
        loader.load("""
                objectClasses:
                  Secure:
                    attributes:
                      password:
                        readable: false
                        connId:
                          type: GuardedString
                """);

        builder.applyStructuralRules();
        var password = loader.build().objectClass("Secure").attributeFromProtocolName("password").connId();

        assertEquals(password.getType(), GuardedString.class);
        assertFalse(password.isReadable());
    }

    @Test
    public void unknownConnIdTypeFails() {
        var exception = expectThrows(IllegalArgumentException.class, () -> new YamlSchemaLoader(schemaBuilder()).load("""
                objectClasses:
                  Broken:
                    attributes:
                      a:
                        connId:
                          type: uuid
                """));

        assertTrue(exception.getMessage().contains("uuid"), exception.getMessage());
    }

    @Test
    public void jsonPathIsBoundWithTheDefaultFormat() {
        var builder = schemaBuilder();
        var loader = new YamlSchemaLoader(builder);
        loader.load("""
                objectClasses:
                  User:
                    attributes:
                      email:
                        json:
                          type: string
                          path: $.emails[?(@.primary == true)].value
                """);

        builder.applyStructuralRules();
        var email = loader.build().objectClass("User").attributeFromProtocolName("email");
        var mapping = email.json();

        assertEquals(mapping.pathDeclaration().type().value(), BasicJsonPathFormat.INSTANCE);
        assertEquals(mapping.pathDeclaration().value().value(), "$.emails[?(@.primary == true)].value");
        assertEquals(mapping.pathDeclaration().value().location().line(), 7);
        assertEquals(mapping.path().components(), List.of(
                new AttributePath.Attribute("emails"),
                new AttributePath.SimpleValueFilter(Map.of("primary", Boolean.TRUE)),
                new AttributePath.Attribute("value")));
    }

    @Test
    public void jsonPathBindingSupportsAnExplicitFormat() {
        var builder = schemaBuilder();
        var loader = new YamlSchemaLoader(builder);
        loader.load("""
                objectClasses:
                  User:
                    attributes:
                      email:
                        json:
                          type: string
                          path:
                            type: JSON_POINTER
                            value: /emails/0/value
                """);

        builder.applyStructuralRules();
        var email = loader.build().objectClass("User").attributeFromProtocolName("email");
        var mapping = email.json();

        assertEquals(mapping.pathDeclaration().type().value(), JsonPointerFormat.INSTANCE);
        assertEquals(mapping.pathDeclaration().value().value(), "/emails/0/value");
        assertEquals(mapping.path().components(), List.of(
                new AttributePath.Attribute("emails"),
                new AttributePath.IndexFilter(0),
                new AttributePath.Attribute("value")));
    }

    @Test
    public void invalidJsonPathFailsAtBuildNamingTheExpression() {
        var builder = schemaBuilder();
        var loader = new YamlSchemaLoader(builder);
        loader.load("""
                objectClasses:
                  User:
                    attributes:
                      email:
                        json:
                          type: string
                          path: $.emails[?(@.primary == )]
                """);

        // the structural rules force the mapping build, which forces the lazy path parse
        var exception = expectThrows(ParsingException.class, builder::applyStructuralRules);

        assertTrue(exception.getMessage().contains("$.emails[?(@.primary == )]"), exception.getMessage());
    }

    /**
     * A protocol-specific object class builder (e.g. a connector's {@code sql}/{@code scim} block)
     * opts in to receiving unrecognized top-level YAML keys by implementing this.
     */
    @SuppressWarnings("unchecked")
    private static final class StubProtocolAwareObjectClass extends BaseObjectClassDefinitionBuilder
            implements YamlProtocolBlockConsumer {

        String capturedName;
        JsonNode capturedBlock;

        StubProtocolAwareObjectClass(BaseSchemaBuilder schemaBuilder, DefinitionValue<String> name) {
            super(schemaBuilder, name);
        }

        @Override
        public void applyProtocolBlock(String name, JsonNode block) {
            this.capturedName = name;
            this.capturedBlock = block;
        }
    }

    @SuppressWarnings("unchecked")
    private static final class StubProtocolAwareSchemaBuilder extends BaseSchemaBuilder {
        StubProtocolAwareSchemaBuilder() {
            super(StubConnector.class, NOOP_CONTEXT);
        }

        @Override
        protected StubProtocolAwareObjectClass newObjectClass(DefinitionValue name) {
            return new StubProtocolAwareObjectClass(this, name);
        }
    }

    @Test
    public void unknownTopLevelBlockIsDispatchedToProtocolBlockConsumer() {
        var schemaBuilder = new StubProtocolAwareSchemaBuilder();
        new YamlSchemaLoader(schemaBuilder).load("""
                objectClasses:
                  Widget:
                    sql:
                      table: widgets
                      schema: public
                """);

        var widget = (StubProtocolAwareObjectClass) schemaBuilder.objectClass("Widget");
        assertEquals(widget.capturedName, "sql");
        assertEquals(widget.capturedBlock.get("table").asString(), "widgets");
        assertEquals(widget.capturedBlock.get("schema").asString(), "public");
    }

    /** Without a consumer, an unrecognized top-level block fails fast exactly like a typo'd key. */
    @Test
    public void unknownTopLevelBlockWithoutConsumerFailsFast() {
        var exception = expectThrows(IllegalArgumentException.class, () -> new YamlSchemaLoader(schemaBuilder()).load("""
                objectClasses:
                  Widget:
                    sql:
                      table: widgets
                """));

        assertTrue(exception.getMessage().contains("sql"), exception.getMessage());
    }
}
