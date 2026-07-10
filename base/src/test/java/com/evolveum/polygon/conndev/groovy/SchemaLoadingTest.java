/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.groovy;

import com.evolveum.polygon.conndev.schema.BaseAttributeDefinition;
import com.evolveum.polygon.conndev.schema.BaseObjectClassDefinition;
import com.evolveum.polygon.conndev.schema.BaseSchema;
import com.evolveum.polygon.conndev.schema.BaseSchemaBuilder;
import com.evolveum.polygon.conndev.api.ContextLookup;
import org.identityconnectors.framework.common.objects.Uid;
import org.identityconnectors.framework.common.objects.Name;
import org.identityconnectors.framework.spi.Connector;
import org.testng.annotations.Test;

import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests that Groovy schema definition scripts from test resources are correctly parsed
 * into ConnId schema objects. Each test loads a Groovy script and asserts on the
 * resulting {@link BaseSchema} structure.
 *
 * <p>These tests exercise the full parser path:
 * Groovy shell → DelegatesTo builder closures → {@link BaseSchemaBuilder} → parsed {@link BaseSchema}
 */
public class SchemaLoadingTest {

    // ========================================================================
    // Helpers
    // ========================================================================

    /**
     * Stub connector used as parameter requirement for BaseSchemaBuilder.
     * No actual connector operations are performed during parsing tests.
     */
    private static final class StubConnector implements Connector {
        @Override public org.identityconnectors.framework.spi.Configuration getConfiguration() { return null; }
        @Override public void init(org.identityconnectors.framework.spi.Configuration c) {}
        @Override public void dispose() {}
    }

    /**
     * No-op ContextLookup: tests load schema only (no scripts executed at runtime).
     * ContextLookup has a generic method that Java lambda inference cannot handle,
     * so we use an anonymous class.
     */
    private static final ContextLookup NOOP_CONTEXT = ContextLookup.none();


    /**
     * Returns a harness with a publicly accessible GroovyShell.
     * Useful for AssertJ exception matchers and multi-script tests.
     */
    private SchemaHarness harness() {
        var builder = new BaseSchemaBuilder(StubConnector.class, NOOP_CONTEXT);
        var context = new GroovyContext();
        var shell = context.createShell();
        shell.setVariable("objectClass", new org.codehaus.groovy.runtime.MethodClosure(builder, "objectClass"));
        shell.setVariable("relationship", new org.codehaus.groovy.runtime.MethodClosure(builder, "relationship"));
        return new SchemaHarness(builder, shell);
    }

    /**
     * Loads a Groovy script resource into a schema, using a single BaseSchemaBuilder instance.
     * The returned harness lets you call {@link SchemaHarness#build()}, {@link SchemaHarness#objectClass(String)},
     * {@link SchemaHarness#attribute(String, String)}, and {@link SchemaHarness#shell()}.
     *
     * @param resourcePath classpath resource with a Groovy objectClass/relationship script
     */
    private SchemaHarness schemaForResource(String resourcePath) {
        return harness().loadFromResource(resourcePath);
    }

    /**
     * Evaluates an inline Groovy script and returns a harness for assertions.
     * Uses a single BaseSchemaBuilder so scripts can define multiple object classes.
     *
     * @param script Groovy DSL script (objectClass/relationship blocks)
     */
    private SchemaHarness schemaForScript(String script) {
        return harness().loadInline(script);
    }


    // ========================================================================
    // 1. ForgejoMinimalUser - basic user schema with ConnId UID/NAME mappings
    // ========================================================================

    @Test
    public void forgejoMinimalUser_parsesAllAttributes() {
        var schema = schemaForResource("/schema/ForgejoMinimalUser.groovy");

        BaseObjectClassDefinition<BaseAttributeDefinition> def = schema.objectClass("User");
        assertThat(def).isNotNull();
        assertThat(def.name()).isEqualTo("User");
        assertThat(def.attributes()).hasSize(5);
    }

    @Test
    public void forgejoMinimalUser_emailHasEmailFormat() {
        var schema = schemaForResource("/schema/ForgejoMinimalUser.groovy");

        BaseAttributeDefinition emailAttr = schema.attribute("User", "email");
        assertThat(emailAttr).isNotNull();
        assertThat(emailAttr.connId().getType()).isEqualTo(String.class);
        assertThat(emailAttr.connId().isUpdateable()).isTrue();
        assertThat(emailAttr.connId().getDescription()).isEqualTo("The user's email address");
    }

    @Test
    public void forgejoMinimalUser_idHasInt64Format() {
        var schema = schemaForResource("/schema/ForgejoMinimalUser.groovy");

        BaseAttributeDefinition idAttr = schema.attribute("User", "id");
        //** Id is

        assertThat(idAttr.connId().getType())
                .withFailMessage("ConnId type should be String, because it is mapped as UID attribute")
                .isEqualTo(String.class);
        assertThat(idAttr.connId().getDescription()).isEqualTo("The unique identifier for the user");
    }

    @Test
    public void forgejoMinimalUser_booleanAttribute() {
        var schema = schemaForResource("/schema/ForgejoMinimalUser.groovy");

        BaseAttributeDefinition activeAttr = schema.attribute("User", "active");
        assertThat(activeAttr.connId().getType()).isEqualTo(Boolean.class);
        assertThat(activeAttr.connId().isUpdateable()).isTrue();
    }

    @Test
    public void forgejoMinimalUser_connId_UID_maps_id() {
        var schema = schemaForResource("/schema/ForgejoMinimalUser.groovy");

        BaseAttributeDefinition uidAttr = schema.objectClass("User").attributeFromConnIdName(Uid.NAME);
        assertThat(uidAttr).isNotNull();
        assertThat(uidAttr.remoteName()).isEqualTo("id");
    }

    @Test
    public void forgejoMinimalUser_connId_NAME_maps_login() {
        var schema = schemaForResource("/schema/ForgejoMinimalUser.groovy");

        BaseAttributeDefinition nameAttr = schema.objectClass("User").attributeFromConnIdName(Name.NAME);
        assertThat(nameAttr).isNotNull();
        assertThat(nameAttr.remoteName()).isEqualTo("login");
    }

    @Test
    public void forgejoMinimalUser_stringAttributes() {
        var schema = schemaForResource("/schema/ForgejoMinimalUser.groovy");

        // full_name and login should both be String type
        assertThat(schema.attribute("User", "full_name").connId().getType()).isEqualTo(String.class);
        assertThat(schema.attribute("User", "login").connId().getType()).isEqualTo(String.class);
    }

    // ========================================================================
    // 2. NextCloud User - standard user schema with groups (multi-valued)
    // ========================================================================

    @Test
    public void nextcloudUser_parsesAllAttributes() {
        var schema = schemaForResource("/nextCloud/User.groovy");

        BaseObjectClassDefinition<BaseAttributeDefinition> def = schema.objectClass("User");
        assertThat(def).isNotNull();
        assertThat(def.name()).isEqualTo("User");
        assertThat(def.attributes()).hasSize(8);
    }

    @Test
    public void nextcloudUser_useridAttribute() {
        var schema = schemaForResource("/nextCloud/User.groovy");

        BaseAttributeDefinition attr = schema.attribute("User", "userid");
        assertThat(attr.connId().getType()).isEqualTo(String.class);
        assertThat(attr.connId().getDescription()).isEqualTo("the required username for the new user");
    }

    @Test
    public void nextcloudUser_groupsIsMultiValued() {
        var schema = schemaForResource("/nextCloud/User.groovy");

        BaseAttributeDefinition groups = schema.attribute("User", "groups");
        assertThat(groups.connId().isMultiValued()).isTrue();
        assertThat(groups.connId().getDescription()).isEqualTo("the groups for the new user");
    }

    @Test
    public void nextcloudUser_subadminIsMultiValued() {
        var schema = schemaForResource("/nextCloud/User.groovy");

        BaseAttributeDefinition subadmin = schema.attribute("User", "subadmin");
        assertThat(subadmin.connId().isMultiValued()).isTrue();
    }

    @Test
    public void nextcloudUser_allAttributesAreStrings() {
        var schema = schemaForResource("/nextCloud/User.groovy");

        for (BaseAttributeDefinition attr : schema.objectClass("User").attributes()) {
            // NextCloud User all uses jsonType "string" or "array" (multiValued string)
            // which still maps to String as the ConnId type
            assertThat(attr.connId().getType()).isEqualTo(String.class);
        }
    }

    // ========================================================================
    // 3. NextCloud Group - schema with int64 fields
    // ========================================================================

    @Test
    public void nextCloudGroup_parsesAttributes() {
        var schema = schemaForResource("/nextCloud/Group.groovy");

        BaseObjectClassDefinition<BaseAttributeDefinition> def = schema.objectClass("Group");
        assertThat(def).isNotNull();
        assertThat(def.name()).isEqualTo("Group");
        assertThat(def.attributes()).hasSize(3);
    }

    @Test
    public void nextCloudGroup_searchIsString() {
        var schema = schemaForResource("/nextCloud/Group.groovy");

        BaseAttributeDefinition search = schema.attribute("Group", "search");
        assertThat(search.connId().getType()).isEqualTo(String.class);
    }

    @Test
    public void nextCloudGroup_limitHasInt64Format() {
        var schema = schemaForResource("/nextCloud/Group.groovy");

        BaseAttributeDefinition limit = schema.attribute("Group", "limit");
        assertThat(limit.connId().getType()).isEqualTo(Long.class);
        assertThat(limit.connId().getDescription()).isEqualTo("optional limit value");
    }

    @Test
    public void nextCloudGroup_offsetHasInt64Format() {
        var schema = schemaForResource("/nextCloud/Group.groovy");

        BaseAttributeDefinition offset = schema.attribute("Group", "offset");
        assertThat(offset.connId().getType()).isEqualTo(Long.class);
    }

    // ========================================================================
    // 4. NextCloud App - simplest schema (single attribute)
    // ========================================================================

    @Test
    public void nextCloudApp_parsesSingleAttribute() {
        var schema = schemaForResource("/nextCloud/App.groovy");

        BaseObjectClassDefinition<BaseAttributeDefinition> def = schema.objectClass("App");
        assertThat(def).isNotNull();
        assertThat(def.name()).isEqualTo("App");
        assertThat(def.attributes()).hasSize(1);

        BaseAttributeDefinition filter = schema.attribute("App", "filter");
        assertThat(filter.connId().getType()).isEqualTo(String.class);
        assertThat(filter.connId().getDescription()).isEqualTo("optional (enabled or disabled)");
    }

    // ========================================================================
    // 5. OpenProject User - complex schema with mixed read-only/mutable
    // ========================================================================

    @Test
    public void openProjectUser_parsesAllAttributes() {
        var schema = schemaForResource("/openProject/User.groovy");

        BaseObjectClassDefinition<BaseAttributeDefinition> def = schema.objectClass("User");
        assertThat(def).isNotNull();
        assertThat(def.name()).isEqualTo("User");
        assertThat(def.attributes()).hasSize(14);
    }

    @Test
    public void openProjectUser_idIsReadOnly() {
        var schema = schemaForResource("/openProject/User.groovy");

        BaseAttributeDefinition id = schema.attribute("User", "id");
        assertThat(id.connId().getType()).isEqualTo(Long.class); // int64 → Long
        assertThat(id.connId().isCreateable()).isFalse();
        assertThat(id.connId().isUpdateable()).isFalse();
    }

    @Test
    public void openProjectUser_passwordNotReadable() {
        var schema = schemaForResource("/openProject/User.groovy");

        BaseAttributeDefinition password = schema.attribute("User", "password");
        assertThat(password.connId().isReadable()).isFalse();
        // readable=false should also set returnedByDefault=false
        assertThat(password.connId().isReturnedByDefault()).isFalse();
    }

    @Test
    public void openProjectUser_readOnlyAttributes() {
        var schema = schemaForResource("/openProject/User.groovy");

        // Several attributes in OpenProject User are read-only (creatable+updatable=false)
        for (String attrName : new String[]{"id", "name", "avatar", "status", "createdAt", "updatedAt"}) {
            BaseAttributeDefinition attr = schema.attribute("User", attrName);
            assertThat(attr)
                .as("Attribute %s should be read-only", attrName)
                .isNotNull();
            assertThat(attr.connId().isCreateable())
                .as("Attribute %s should not be creatable", attrName)
                .isFalse();
            assertThat(attr.connId().isUpdateable())
                .as("Attribute %s should not be updateable", attrName)
                .isFalse();
        }
    }

    @Test
    public void openProjectUser_emailHasEmailFormat() {
        var schema = schemaForResource("/openProject/User.groovy");

        assertThat(schema.attribute("User", "email").connId().getType()).isEqualTo(String.class);
    }

    @Test
    public void openProjectUser_createdAtHasDateTimeFormat() {
        var schema = schemaForResource("/openProject/User.groovy");

        assertThat(schema.attribute("User", "createdAt").connId().getType()).isEqualTo(ZonedDateTime.class);
    }

    @Test
    public void openProjectUser_updatableAttributes() {
        var schema = schemaForResource("/openProject/User.groovy");

        // login, firstName, lastName, language are writable
        for (String attrName : new String[]{"login", "firstName", "lastName", "language"}) {
            assertThat(schema.attribute("User", attrName).connId().getType()).isEqualTo(String.class);
        }

        // admin is boolean
        assertThat(schema.attribute("User", "admin").connId().getType()).isEqualTo(Boolean.class);
    }

    // ========================================================================
    // 6. OpenProject Project - project schema with optional fields
    // ========================================================================

    @Test
    public void openProjectProject_parsesAllAttributes() {
        var schema = schemaForResource("/openProject/Project.groovy");
        BaseObjectClassDefinition<BaseAttributeDefinition> def = schema.objectClass("Project");
        assertThat(def).isNotNull();
        assertThat(def.name()).isEqualTo("Project");
        assertThat(def.attributes()).hasSize(9);
    }

    @Test
    public void openProjectProject_idAndBooleans() {
        var schema = schemaForResource("/openProject/Project.groovy");

        assertThat(schema.attribute("Project", "id").connId().getType()).isEqualTo(Long.class);
        assertThat(schema.attribute("Project", "active").connId().getType()).isEqualTo(Boolean.class);
        assertThat(schema.attribute("Project", "public").connId().getType()).isEqualTo(Boolean.class);
    }

    @Test
    public void openProjectProject_readOnlyTimestamps() {
        var schema = schemaForResource("/openProject/Project.groovy");

        assertThat(schema.attribute("Project", "createdAt").connId().isCreateable()).isFalse();
        assertThat(schema.attribute("Project", "createdAt").connId().isUpdateable()).isFalse();
        assertThat(schema.attribute("Project", "updatedAt").connId().isCreateable()).isFalse();
        assertThat(schema.attribute("Project", "updatedAt").connId().isUpdateable()).isFalse();
    }


    // ========================================================================
    // 7. OpenProject Group - read-only group schema
    // ========================================================================

    @Test
    public void openProjectGroup_parsesAttributes() {
        var schema = schemaForResource("/openProject/Group.groovy");
        BaseObjectClassDefinition<BaseAttributeDefinition> def = schema.objectClass("Group");
        assertThat(def).isNotNull();
        assertThat(def.name()).isEqualTo("Group");
        assertThat(def.attributes()).hasSize(4);
    }

    @Test
    public void openProjectGroup_idIsInt64() {
        var schema = schemaForResource("/openProject/Group.groovy");
        assertThat(schema.attribute("Group", "id").connId().getType()).isEqualTo(Long.class);
    }

    // ========================================================================
    // 8. OpenProject Role - minimal read-only schema
    // ========================================================================

    @Test
    public void openProjectRole_parsesAllAttributes() {
        var schema = schemaForResource("/openProject/Role.groovy");
        BaseObjectClassDefinition<BaseAttributeDefinition> def = schema.objectClass("Role");
        assertThat(def).isNotNull();
        assertThat(def.name()).isEqualTo("Role");
        assertThat(def.attributes()).hasSize(2);
    }

    @Test
    public void openProjectRole_allReadOnly() {
        var schema = schemaForResource("/openProject/Role.groovy");

        assertThat(schema.attribute("Role", "id").connId().isCreateable()).isFalse();
        assertThat(schema.attribute("Role", "id").connId().isUpdateable()).isFalse();
        assertThat(schema.attribute("Role", "name").connId().isCreateable()).isFalse();
        assertThat(schema.attribute("Role", "name").connId().isUpdateable()).isFalse();
    }

    @Test
    public void openProjectRole_idIsInt64() {
        var schema = schemaForResource("/openProject/Role.groovy");

        assertThat(schema.attribute("Role", "id").connId().getType()).isEqualTo(Long.class);
        assertThat(schema.attribute("Role", "name").connId().getType()).isEqualTo(String.class);
    }

    // ========================================================================
    // 9. Integration: multiple object classes from separate scripts
    // ========================================================================

    @Test
    public void loadMultipleScripts_createsMultipleObjectClasses() {
        var schema = harness();

        schema.loadFromResource("/nextCloud/User.groovy");
        schema.loadFromResource("/nextCloud/Group.groovy");


        assertThat(schema.objectClass("User")).isNotNull();
        assertThat(schema.objectClass("Group")).isNotNull();

        // User should have 8 attributes
        assertThat(schema.objectClass("User").attributes()).hasSize(8);
        // Group should have 3 attributes  
        assertThat(schema.objectClass("Group").attributes()).hasSize(3);
    }

    @Test
    public void loadMultipleScripts_fromOpenProject() {
        var schema = harness()
                .loadFromResource("/openProject/User.groovy")
                .loadFromResource("/openProject/Project.groovy")
                .loadFromResource("/openProject/Group.groovy")
                .loadFromResource("/openProject/Role.groovy");

        assertThat(schema.objectClass("User")).isNotNull();
        assertThat(schema.objectClass("Project")).isNotNull();
        assertThat(schema.objectClass("Group")).isNotNull();
        assertThat(schema.objectClass("Role")).isNotNull();
    }

    // ========================================================================
    // 10. JSON path resolution from parsed attributes
    // ========================================================================

    @Test
    public void forgejoMinimalUser_jsonMappingExists() {
        var schema = schemaForResource("/schema/ForgejoMinimalUser.groovy");

        // Verify the email attribute has a JSON mapping
        var jsonMap = schema.attribute("User", "email").json();
        assertThat(jsonMap).isNotNull();
    }

    // ========================================================================
    // 11. ConnectionId attributes on multiple object classes
    // ========================================================================

    @Test
    public void connIdAttributesAcrossObjectClasses() {
        var schema = schemaForResource("/schema/ForgejoMinimalUser.groovy");

        // UID and NAME should both be mapped
        assertThat(schema.objectClass("User").attributeFromConnIdName(Uid.NAME)).isNotNull();
        assertThat(schema.objectClass("User").attributeFromConnIdName(Name.NAME)).isNotNull();
    }

    // ========================================================================
    // 12. Error handling: scripts that reference non-existent ConnId attributes
    // ========================================================================

    @Test
    public void invalidConnIdAttribute_throwsException() {
        var schema = harness();

        // This script uses a ConnId attribute name that doesn't exist
        String badScript = """
            objectClass("Test") {
                attribute("x") {
                    jsonType "string";
                };
                connIdAttribute "INVALID", "x"
            }
            """;

        assertThatThrownBy(() -> schema.shell().evaluate(badScript))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("No such built-in ConnID attribute");
    }

    // ========================================================================
    // 13. ObjectClass-level properties
    // ========================================================================

    @Test
    public void embeddedObjectClass_parsed() {
        var schema = schemaForScript("""
            objectClass("EmbeddedData") {
                embedded true;
                attribute("payload") {
                    jsonType "string";
                    description "embedded data";
                };
            }
            """);

        BaseObjectClassDefinition<BaseAttributeDefinition> def = schema.objectClass("EmbeddedData");

        assertThat(def).isNotNull();
        assertThat(def.connId().getType()).isEqualTo("EmbeddedData");
    }

    @Test
    public void objectClassWithDescription_parsed() {
        var schema = schemaForScript("""
            objectClass("Config") {
                description "System configuration object";
                attribute("value") {
                    jsonType "string";
                };
            }
            """);

        BaseObjectClassDefinition<BaseAttributeDefinition> def = schema.objectClass("Config");

        assertThat(def).isNotNull();
        assertThat(def.connId().getDescription()).isEqualTo("System configuration object");
    }

    // ========================================================================
    // 14. Attribute flags from scripts
    // ========================================================================

    @Test
    public void requiredAttribute_parsed() {
        var schema = schemaForScript("""
            objectClass("Test") {
                attribute("email") {
                    jsonType "string";
                    required true;
                    description "User's email";
                };
            }
            """);

        BaseAttributeDefinition email = schema.attribute("Test", "email");
        assertThat(email.connId().isRequired()).isTrue();
        assertThat(email.connId().getDescription()).isEqualTo("User's email");
    }

    @Test
    public void multiValuedAttribute_parsed() {
        var schema = schemaForScript("""
            objectClass("Test") {
                attribute("tags") {
                    jsonType "string";
                    multiValued true;
                };
            }
            """);

        BaseAttributeDefinition tags = schema.attribute("Test", "tags");
        assertThat(tags.connId().isMultiValued()).isTrue();
    }

    // ========================================================================
    // 15. Built-in schema: empty schema produces __Dummy
    // ========================================================================

    @Test
    public void emptySchemaProducesDummy() {
        // No scripts loaded at all - use harness without shell for bare builder
        var builder = new BaseSchemaBuilder(StubConnector.class, NOOP_CONTEXT);
        var schema = builder.build();

        // Should contain a __Dummy object class
        BaseObjectClassDefinition<BaseAttributeDefinition> dummy = schema.objectClass("__Dummy");
        assertThat(dummy).isNotNull();
        assertThat(dummy.attributes()).isNotEmpty();
    }

    @Test
    public void dummySchemaHasUidAndName() {
        var builder = new BaseSchemaBuilder(StubConnector.class, NOOP_CONTEXT);
        var schema = builder.build();
        BaseObjectClassDefinition<BaseAttributeDefinition> dummy = schema.objectClass("__Dummy");

        assertThat(dummy.attributeFromConnIdName(Uid.NAME)).isNotNull();
        assertThat(dummy.attributeFromConnIdName(Name.NAME)).isNotNull();
    }

    // ========================================================================
    // 16. All jsonType values from example scripts are correctly mapped
    // ========================================================================

    @Test
    public void stringJsonType_mapsCorrectly() {
        var schema = schemaForScript("""
            objectClass("Test") {
                attribute("name") { jsonType "string"; };
                attribute("code") { jsonType "string"; };
            }
            """);

        assertThat(schema.attribute("Test", "name").connId().getType()).isEqualTo(String.class);
        assertThat(schema.attribute("Test", "code").connId().getType()).isEqualTo(String.class);
    }

    @Test
    public void integerJsonType_mapsCorrectly() {
        var schema = schemaForScript("""
            objectClass("Test") {
                attribute("count") { jsonType "integer"; };
            }
            """);

        assertThat(schema.attribute("Test", "count").connId().getType()).isEqualTo(Integer.class);
    }

    @Test
    public void booleanJsonType_mapsCorrectly() {
        var schema = schemaForScript("""
            objectClass("Test") {
                attribute("active") { jsonType "boolean"; };
            }
            """);

        assertThat(schema.attribute("Test", "active").connId().getType()).isEqualTo(Boolean.class);
    }

    @Test
    public void intJsonType_withInt64Format_mapsToLong() {
        var schema = schemaForScript("""
            objectClass("Test") {
                attribute("id") {
                    jsonType "int";
                    openApiFormat "int64";
                };
            }
            """);

        assertThat(schema.attribute("Test", "id").connId().getType()).isEqualTo(Long.class);
    }

    @Test
    public void intJsonType_withoutFormat_mapsToInteger() {
        var schema = schemaForScript("""
            objectClass("Test") {
                attribute("count") {
                    json {
                        type "integer"
                    }
                }
            }
            """);

        assertThat(schema.attribute("Test", "count").connId().getType()).isEqualTo(Integer.class);
    }

    // ========================================================================
    // 17. OpenAPI format resolution from scripts
    // ========================================================================

    @Test
    public void openApiFormat_email() {
        var schema = schemaForScript("""
            objectClass("Test") {
                attribute("email") {
                    jsonType "string";
                    openApiFormat "email";
                };
            }
            """);

        assertThat(schema.attribute("Test", "email").connId().getType()).isEqualTo(String.class);
    }

    @Test
    public void openApiFormat_int64() {
        var schema = schemaForScript("""
            objectClass("Test") {
                attribute("id") {
                    jsonType "int";
                    openApiFormat "int64";
                };
            }
            """);

        assertThat(schema.attribute("Test", "id").connId().getType()).isEqualTo(Long.class);
    }

    @Test
    public void openApiFormat_dateTime_mapsToZonedDateTime() {
        var schema = schemaForScript("""
            objectClass("Test") {
                attribute("createdAt") {
                    jsonType "string";
                    openApiFormat "date-time";
                };
            }
            """);

        assertThat(schema.attribute("Test", "createdAt").connId().getType()).isEqualTo(ZonedDateTime.class);
    }

    @Test
    public void openApiFormat_uuid_mapsToString() {
        var schema = schemaForScript("""
            objectClass("Test") {
                attribute("externalId") {
                    jsonType "string";
                    openApiFormat "uuid";
                };
            }
            """);

        assertThat(schema.attribute("Test", "externalId").connId().getType()).isEqualTo(String.class);
    }

    // ========================================================================
    // 18. Real-world schema: NextCloud full test (User + Group + App)
    // ========================================================================

    @Test
    public void nextcloud_fullSchema_parsesAllObjectClasses() {
        var schema = harness().loadFromResource("/nextCloud/User.groovy")
                          .loadFromResource("/nextCloud/Group.groovy")
                          .loadFromResource("/nextCloud/App.groovy");

        assertThat(schema.objectClass("User")).isNotNull();
        assertThat(schema.objectClass("Group")).isNotNull();
        assertThat(schema.objectClass("App")).isNotNull();

        // Verify attribute counts match each script's definition
        assertThat(schema.objectClass("User").attributes()).hasSize(8);
        assertThat(schema.objectClass("Group").attributes()).hasSize(3);
        assertThat(schema.objectClass("App").attributes()).hasSize(1);
    }

    @Test
    public void openproject_fullSchema_parsesAllObjectClasses() {
        var schema = harness().loadFromResource("/openProject/User.groovy")
                         .loadFromResource("/openProject/Project.groovy")
                         .loadFromResource("/openProject/Group.groovy")
                         .loadFromResource("/openProject/Role.groovy");

        assertThat(schema.objectClass("User")).isNotNull();
        assertThat(schema.objectClass("Project")).isNotNull();
        assertThat(schema.objectClass("Group")).isNotNull();
        assertThat(schema.objectClass("Role")).isNotNull();

        // Verify attribute counts
        assertThat(schema.objectClass("User").attributes()).hasSize(14);
        assertThat(schema.objectClass("Project").attributes()).hasSize(9);
        assertThat(schema.objectClass("Group").attributes()).hasSize(4);
        assertThat(schema.objectClass("Role").attributes()).hasSize(2);
    }

    // ========================================================================
    // 19. Script evaluation via GroovyShell directly with full DSL
    // ========================================================================

    @Test
    public void fullDslEvaluation_scriptExecuted() {
        var schema = schemaForScript("""
            objectClass("Product") {
                attribute("id") {
                    jsonType "integer";
                    openApiFormat "int64";
                    description "Product identifier";
                };
                attribute("name") {
                    jsonType "string";
                    required true;
                    description "Product name";
                };
                attribute("sku") {
                    jsonType "string";
                    openApiFormat "uuid";
                    description "Stock keeping unit";
                };
                attribute("active") {
                    jsonType "boolean";
                    description "Whether product is active";
                };
                attribute("tags") {
                    jsonType "string";
                    multiValued true;
                    description "Product tags";
                };
            }
            """);

        BaseObjectClassDefinition<BaseAttributeDefinition> def = schema.objectClass("Product");

        assertThat(def).isNotNull();
        assertThat(def.attributes()).hasSize(5);

        // Verify specific attributes
        assertThat(schema.attribute("Product", "id").connId().getDescription()).isEqualTo("Product identifier");
        assertThat(schema.attribute("Product", "id").connId().getType()).isEqualTo(Long.class);

        assertThat(schema.attribute("Product", "name").connId().isRequired()).isTrue();
        assertThat(schema.attribute("Product", "sku").connId().getDescription()).isEqualTo("Stock keeping unit");
        assertThat(schema.attribute("Product", "tags").connId().isMultiValued()).isTrue();
    }

    @Test
    public void fullDslEvaluation_withConnIdMappings() {
        var schema = schemaForScript("""
            objectClass("Widget") {
                attribute("widgetId") {
                    jsonType "string";
                    description "Internal ID";
                };
                attribute("widgetName") {
                    jsonType "string";
                    required true;
                };
                connIdAttribute ("UID", "widgetId")
                connIdAttribute ("NAME", "widgetName")
            }
            """);

        // ConnId attributes mapped correctly
        assertThat(schema.objectClass("Widget").attributeFromConnIdName(Uid.NAME)).isNotNull();
        assertThat(schema.objectClass("Widget").attributeFromConnIdName(Uid.NAME).remoteName()).isEqualTo("widgetId");
        assertThat(schema.objectClass("Widget").attributeFromConnIdName(Name.NAME)).isNotNull();
        assertThat(schema.objectClass("Widget").attributeFromConnIdName(Name.NAME).remoteName()).isEqualTo("widgetName");

        // Protocol attributes still accessible
        assertThat(schema.objectClass("Widget").attributeFromProtocolName("widgetId")).isNotNull();
        assertThat(schema.objectClass("Widget").attributeFromProtocolName("widgetName")).isNotNull();
    }
}