/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 */
package com.evolveum.polygon.conndev.groovy;

import com.evolveum.polygon.conndev.api.ContextLookup;
import com.evolveum.polygon.conndev.json.JsonAttributeMapping;
import com.evolveum.polygon.conndev.json.JsonSchemaValueMapping;
import com.evolveum.polygon.conndev.schema.BaseSchema;
import com.evolveum.polygon.conndev.schema.BaseSchemaBuilder;
import com.evolveum.polygon.conndev.schema.StubConnector;
import com.evolveum.polygon.conndev.yaml.YamlSchemaLoader;
import org.codehaus.groovy.runtime.MethodClosure;
import org.identityconnectors.framework.common.objects.*;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

public class JsonUidReferenceTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String GROOVY = """
            objectClass('User') {
                reference('groups') {
                    objectClass 'Group'
                    multiValued true
                    json { referenceBy 'uid' }
                }
            }
            objectClass('Group') {
                reference('members') {
                    role 'object'
                    subtype 'UserGroupMembership'
                    multiValued true
                    json {
                        path attribute('directory').child('members')
                        referenceBy {
                            uidFrom 'id'
                            nameFrom 'title'
                        }
                    }
                    objectClass 'User'
                }
            }
            """;
    private static final String YAML = """
            objectClasses:
              User:
                references:
                  groups:
                    objectClass: Group
                    multiValued: true
                    json:
                      referenceBy: uid
              Group:
                references:
                  members:
                    role: object
                    subtype: UserGroupMembership
                    multiValued: true
                    json:
                      path: $.directory.members
                      referenceBy:
                        uidFrom: id
                        nameFrom: title
                    objectClass: User
            """;

    private static BaseSchema groovy(String script) {
        var builder = new BaseSchemaBuilder(StubConnector.class, ContextLookup.none());
        var shell = new GroovyContext().createShell();
        shell.setVariable("objectClass", new MethodClosure(builder, "objectClass"));
        shell.evaluate(script);
        builder.applyStructuralRules();
        return builder.build();
    }

    @DataProvider
    public Object[][] schemas() {
        var builder = new BaseSchemaBuilder(StubConnector.class, ContextLookup.none());
        var loader = new YamlSchemaLoader(builder);
        loader.load(YAML);
        return new Object[][] {{groovy(GROOVY)}, {loader.build()}};
    }

    private static ObjectNode write(JsonAttributeMapping mapping, String attribute, List<?> values) {
        var result = JSON.createObjectNode();
        mapping.toJsonNode(AttributeBuilder.build(attribute, values), result);
        return result;
    }

    private static void assertReference(Object value, String target, String uid, String name) {
        assertThat(value).isInstanceOf(ConnectorObjectReference.class);
        var ref = ((ConnectorObjectReference) value).getValue();
        assertThat(ref.getObjectClass()).isEqualTo(new ObjectClass(target));
        assertThat(AttributeUtil.getUidAttribute(ref.getAttributes())).isEqualTo(new Uid(uid));
        assertThat(AttributeUtil.getNameFromAttributes(ref.getAttributes())).isEqualTo(new Name(name));
    }

    @Test(dataProvider = "schemas")
    public void plainUidsRoundTripAsGroupReferences(BaseSchema schema) {
        var mapping = schema.objectClass("User").attributeFromProtocolName("groups").json();
        var input = JSON.createObjectNode();
        input.putArray("groups").add("id-1").add("id-2");
        var refs = mapping.valuesFromObject(input);
        assertThat(refs).hasSize(2);
        assertReference(refs.getFirst(), "Group", "id-1", "id-1");
        assertReference(refs.getLast(), "Group", "id-2", "id-2");
        assertThat(write(mapping, "groups", refs)).isEqualTo(input);
        assertThat(write(mapping, "groups", List.of()).toString()).isEqualTo("{\"groups\":[]}");
    }

    @Test(dataProvider = "schemas")
    public void objectReferencesReadLabelsAndWriteOnlyIds(BaseSchema schema) {
        var mapping = schema.objectClass("Group").attributeFromProtocolName("members").json();
        var input = JSON.createObjectNode();
        var members = input.putObject("directory").putArray("members");
        members.addObject().put("id", "42").put("title", "Alice");
        members.addObject().put("id", "A/B C+D");
        var refs = mapping.valuesFromObject(input);
        assertThat(refs).hasSize(2);
        assertReference(refs.getFirst(), "User", "42", "Alice");
        assertReference(refs.getLast(), "User", "A/B C+D", "A/B C+D");
        members.get(0).asObject().remove("title");
        assertThat(write(mapping, "members", refs)).isEqualTo(input);
        assertThat(write(mapping, "members", List.of(refs.getFirst())).at("/directory/members").isArray()).isTrue();
        assertThat(write(mapping, "members", List.of()).at("/directory/members").toString()).isEqualTo("[]");
    }

    @Test(dataProvider = "schemas")
    public void invalidReferencesDoNotBecomeOtherObjects(BaseSchema schema) {
        var members = schema.objectClass("Group").attributeFromProtocolName("members").json();
        var groups = schema.objectClass("User").attributeFromProtocolName("groups").json();
        assertThatThrownBy(() -> groups.singleValueFromAttribute(JSON.createObjectNode().put("id", "42")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> members.singleValueFromAttribute(JSON.createObjectNode().put("title", "Alice")))
                .isInstanceOf(IllegalArgumentException.class);
        var wrongClass = new ConnectorObjectBuilder().setObjectClass(new ObjectClass("User")).setUid("42");
        var missingUid = new ConnectorObjectBuilder().setObjectClass(new ObjectClass("Group")).setName("Alice");
        for (var builder : List.of(wrongClass, missingUid)) {
            var ref = new ConnectorObjectReference(builder.buildIdentification());
            assertThatThrownBy(() -> write(groups, "groups", List.of(ref))).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    public void invalidDeclarationsFailWhenBuildingTheSchema() {
        assertThatThrownBy(() -> groovy("objectClass('User') { reference('groups') { json { referenceBy 'uid' } } }"))
                .hasMessageContaining("target objectClass");
        assertThatThrownBy(() -> groovy(GROOVY.replace("referenceBy 'uid'",
                "referenceBy 'uid'; implementation { deserialize { value } }")))
                .hasMessageContaining("either referenceBy or implementation");
    }

    @Test(dataProvider = "schemas")
    public void explicitEmptyIdentifiersAreAbsentAndMalformedItemsFailOnRead(BaseSchema schema) {
        var members = schema.objectClass("Group").attributeFromProtocolName("members").json();
        var emptyId = JSON.createObjectNode().putNull("id");
        assertThat(members.valuesFromAttribute(emptyId)).isNull();
        var items = JSON.createArrayNode().add(emptyId);
        assertThat(members.valuesFromAttribute(items)).isEmpty();
        items.addObject().put("id", "42");
        var refs = members.valuesFromAttribute(items);
        assertThat(refs).hasSize(1);
        assertReference(refs.getFirst(), "User", "42", "42");

        var ids = schema.objectClass("User").attributeFromProtocolName("groups").json();
        assertThatThrownBy(() -> ids.valuesFromAttribute(JSON.createArrayNode().add("good-id").addNull()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Null item");
        assertThat(ids.valuesFromObject(JSON.createObjectNode())).isNull();
    }

    @Test
    public void ordinaryJsonMappingsPreserveTheirExistingArrayNullBehavior() {
        var mapping = new JsonAttributeMapping("values", JsonSchemaValueMapping.STRING);
        assertThat(mapping.valuesFromAttribute(JSON.createArrayNode().add("ok").addNull()))
                .containsExactly("ok", null);
    }

}
