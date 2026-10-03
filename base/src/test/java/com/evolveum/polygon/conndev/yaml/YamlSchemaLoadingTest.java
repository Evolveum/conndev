/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.yaml;

import com.evolveum.polygon.conndev.api.*;
import com.evolveum.polygon.conndev.groovy.GroovyContext;
import com.evolveum.polygon.conndev.groovy.GroovySchemaLoader;
import com.evolveum.polygon.conndev.schema.BaseSchema;
import com.evolveum.polygon.conndev.schema.BaseSchemaBuilder;
import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.framework.common.objects.*;
import org.identityconnectors.framework.spi.Configuration;
import org.identityconnectors.framework.spi.Connector;
import org.testng.annotations.Test;
import tools.jackson.databind.node.JsonNodeFactory;

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
    public void connIdNameKeywordsAreNormalisedToCanonicalNames() {
        var builder = schemaBuilder();
        var loader = new YamlSchemaLoader(builder);
        // the attribute-level connId name uses the built-in convenience keywords (not the canonical
        // __-names); they must resolve to the canonical ConnId names
        loader.load("""
                objectClasses:
                  User:
                    attributes:
                      id:
                        jsonType: integer
                        connId:
                          name: UID
                      login:
                        jsonType: string
                        connId:
                          name: NAME
                      active:
                        jsonType: boolean
                        connId:
                          name: ENABLE
                      last_login:
                        jsonType: string
                        connId:
                          name: LAST_LOGIN_DATE
                """);
        builder.applyStructuralRules();
        var user = loader.build().objectClass("User");

        // the keywords resolve to the canonical ConnId names
        assertEquals(user.attributeFromConnIdName(Uid.NAME).remoteName(), "id");
        assertEquals(user.attributeFromConnIdName(Name.NAME).remoteName(), "login");
        assertEquals(user.attributeFromConnIdName(OperationalAttributes.ENABLE_NAME).remoteName(), "active");
        // a non-keyword value is a literal ConnId attribute name, left as-is
        assertEquals(user.attributeFromConnIdName("LAST_LOGIN_DATE").remoteName(), "last_login");

        // the stored ConnId names carry the canonical values
        assertEquals(user.attributeFromProtocolName("id").connId().getName(), Uid.NAME);
        assertEquals(user.attributeFromProtocolName("active").connId().getName(), OperationalAttributes.ENABLE_NAME);
        assertEquals(user.attributeFromProtocolName("last_login").connId().getName(), "LAST_LOGIN_DATE");
    }

    /**
     * WP #12419: the same attribute mapped to both {@code UID} and {@code NAME} in the
     * class-level alias map must not fail validation — the attribute keeps {@code __UID__}
     * and {@code __NAME__} is derived from it by {@code NameDefaultsToUidRule}.
     */
    @Test
    public void uidAndNameMayShareOneAttributeViaClassLevelAlias() {
        var builder = schemaBuilder();
        var loader = new YamlSchemaLoader(builder);
        loader.load("""
                objectClasses:
                  membership:
                    connId:
                      UID: id
                      NAME: id
                    attributes:
                      id:
                        required: true
                        json:
                          type: integer
                      createdAt:
                        json:
                          type: string
                          openApiFormat: date-time
                """);

        var membership = loader.build().objectClass("membership");

        assertEquals(membership.attributeFromConnIdName(Uid.NAME).remoteName(), "id");
        var nameMapping = membership.attributeFromConnIdName(Name.NAME).json();
        // the derived NAME reads the same wire field as the UID
        assertEquals(nameMapping.path().onlyAttribute().name(), "id");
        var sample = JsonNodeFactory.instance.objectNode().set("id", JsonNodeFactory.instance.numberNode(42));
        assertEquals(nameMapping.singleValueFromAttribute(nameMapping.attributeFromObject(sample)), "42");
        // the UID is forced to the ConnId String type, the derived NAME as well
        assertEquals(membership.attributeFromConnIdName(Uid.NAME).connId().getType(), String.class);
        assertEquals(membership.attributeFromConnIdName(Name.NAME).connId().getType(), String.class);
    }

    /**
     * WP #12419, two-file form: an attribute-level {@code name: UID} claim and an attribute-level
     * {@code name: NAME} claim on the same attribute (merged from separate documents onto one
     * builder) must not conflict.
     */
    @Test
    public void uidAndNameMayShareOneAttributeViaAttributeLevelClaims() {
        var builder = schemaBuilder();
        var loader = new YamlSchemaLoader(builder);
        loader.load("""
                objectClasses:
                  User:
                    attributes:
                      id:
                        jsonType: string
                        connId:
                          name: UID
                """);
        loader.load("""
                objectClasses:
                  User:
                    attributes:
                      id:
                        connId:
                          name: NAME
                """);

        var user = loader.build().objectClass("User");

        // __UID__ wins the slot; __NAME__ is the derived copy
        assertEquals(user.attributeFromProtocolName("id").connId().getName(), Uid.NAME);
        assertNotNull(user.attributeFromConnIdName(Name.NAME));
        assertEquals(user.attributeFromConnIdName(Name.NAME).remoteName(), Name.NAME);
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
    public void guardedStringConnIdTypeOverJsonStringMapping_buildsAndConverts() {
        var builder = schemaBuilder();
        var loader = new YamlSchemaLoader(builder);
        loader.load("""
                objectClasses:
                  Secure:
                    attributes:
                      password:
                        jsonType: string
                        openApiFormat: password
                        connId:
                          type: GuardedString
                """);

        builder.applyStructuralRules();
        var mapping = loader.build().objectClass("Secure").attributeFromProtocolName("password").json();

        assertEquals(mapping.connIdType(), GuardedString.class);
        assertEquals(mapping.singleValueFromAttribute(JsonNodeFactory.instance.stringNode("secret")),
                new GuardedString("secret".toCharArray()));
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
     * The {@code json: implementation: { deserialize: | }} block — the declarative counterpart of the
     * Groovy {@code json { implementation { deserialize { ... } } }} DSL: the fragment's own imports
     * are hoisted, the delegate-provided {@code value} is the wire node, and the built mapping
     * converts through the closure.
     */
    @Test
    public void jsonImplementationBlockBindsDeserialize() {
        var builder = schemaBuilder();
        var loader = new YamlSchemaLoader(builder);
        loader.load("""
                objectClasses:
                  Membership:
                    embedded: true
                    references:
                      project:
                        objectClass: Project
                        json:
                          type: string
                          openApiFormat: uri-reference
                          path: $._links.project
                          implementation:
                            deserialize: |
                              import org.identityconnectors.framework.common.objects.ConnectorObjectBuilder
                              import org.identityconnectors.framework.common.objects.ConnectorObjectReference
                              import org.identityconnectors.framework.common.objects.ObjectClass
                              var href = value.get("href")?.asText()
                              var pid = href.substring(href.lastIndexOf("/") + 1)
                              var obj = new ConnectorObjectBuilder()
                                      .setObjectClass(new ObjectClass("Project"))
                                      .setUid(pid)
                                      .setName(value.get("title")?.asText())
                              return new ConnectorObjectReference(obj.build())
                """);

        builder.applyStructuralRules();
        var mapping = loader.build().objectClass("Membership").attributeFromProtocolName("project").json();
        var sample = JsonNodeFactory.instance.objectNode()
                .set("_links", JsonNodeFactory.instance.objectNode()
                        .set("project", JsonNodeFactory.instance.objectNode()
                                .set("href", JsonNodeFactory.instance.textNode("https://op.example.org/api/v3/projects/123"))
                                .set("title", JsonNodeFactory.instance.textNode("Proj"))));

        var connId = mapping.singleValueFromAttribute(mapping.attributeFromObject(sample));

        assertTrue(connId instanceof ConnectorObjectReference, "expected a ConnectorObjectReference, got " + connId);
        var ref = (ConnectorObject) ((ConnectorObjectReference) connId).getValue();
        assertEquals(ref.getUid(), new Uid("123"));
        assertEquals(ref.getName(), new Name("Proj"));
    }

    /**
     * A {@code type} / {@code openApiFormat} declared after the {@code implementation} block in
     * document order still feeds the lazy base mapping — the Groovy path is order-independent the
     * same way. Both directions compose onto that base.
     */
    @Test
    public void jsonImplementationBeforeTypeStillResolvesTheBase() {
        var builder = schemaBuilder();
        var loader = new YamlSchemaLoader(builder);
        loader.load("""
                objectClasses:
                  Widget:
                    attributes:
                      label:
                        json:
                          implementation:
                            deserialize: |
                              return "d:" + value.asText()
                            serialize: |
                              return "s:" + value
                          type: string
                """);

        builder.applyStructuralRules();
        var mapping = loader.build().objectClass("Widget").attributeFromProtocolName("label").json();

        assertEquals(mapping.singleValueFromAttribute(JsonNodeFactory.instance.stringNode("x")), "d:x");

        var parent = JsonNodeFactory.instance.objectNode();
        mapping.toJsonNode(AttributeBuilder.build("label", "x"), parent);
        assertEquals(parent.get("label").asText(), "s:x");
    }

    /** A typo'd sub-key inside the {@code implementation} block fails fast, naming the key. */
    @Test
    public void unknownKeyInsideJsonImplementationFailsFast() {
        var exception = expectThrows(IllegalArgumentException.class, () -> new YamlSchemaLoader(schemaBuilder()).load("""
                objectClasses:
                  Widget:
                    attributes:
                      label:
                        json:
                          type: string
                          implementation:
                            deserialise: |
                              return value
                """));

        assertTrue(exception.getMessage().contains("deserialise"), exception.getMessage());
    }

    /** An unrecognized object-class-level block (e.g. a protocol block no @Yaml binding declares) fails fast. */
    @Test
    public void unknownTopLevelBlockFailsFast() {
        var exception = expectThrows(IllegalArgumentException.class, () -> new YamlSchemaLoader(schemaBuilder()).load("""
                objectClasses:
                  Widget:
                    sql:
                      table: widgets
                """));

        assertTrue(exception.getMessage().contains("sql"), exception.getMessage());
    }
}
