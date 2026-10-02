/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.schema;

import com.evolveum.polygon.conndev.api.ContextLookup;
import com.evolveum.polygon.conndev.build.api.ValueMappingBuilder;
import com.evolveum.polygon.conndev.concepts.DefinitionValue;
import com.evolveum.polygon.conndev.concepts.SourceLocation;
import groovy.lang.Closure;
import org.identityconnectors.framework.common.objects.ConnectorObject;
import org.identityconnectors.framework.common.objects.ConnectorObjectBuilder;
import org.identityconnectors.framework.common.objects.ConnectorObjectReference;
import org.identityconnectors.framework.common.objects.Name;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.Uid;
import org.testng.annotations.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.JsonNodeFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies that a custom {@code json { implementation { ... } }} value mapping is not
 * bridged against the default JSON mapping's native type (see support bug #12502):
 * a custom implementation owns the wire↔ConnId conversion and produces the attribute's
 * final ConnId type from the connId section (e.g. a {@code ConnectorObjectReference}
 * deserialized from a JSON string), so the schema build must succeed and the built
 * mapping must report the connId section type — not fail with
 * "Unsupported override type combination".
 */
public class CustomImplementationConnIdTypeTest {

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
        return schema.objectClass(DefinitionValue.from(new ObjectClass("Membership"), SourceLocation.capture()));
    }

    /** Applies the structural rules (type resolution + type coercion) and then builds
     * the attribute — the same order the connectors use. */
    private static BaseAttributeDefinition build(TestAttributeBuilder attribute) {
        schema.applyStructuralRules();
        return attribute.build();
    }

    /**
     * The bug #12502 shape: a reference attribute over a string JSON backing whose custom
     * {@code implementation { deserialize { ... } }} produces a
     * {@link ConnectorObjectReference} from the wire value.
     */
    private static TestAttributeBuilder newReferenceAttribute() {
        var attribute = newObjectClass().reference("project");
        attribute.objectClass("Project");
        attribute.json().type("string").openApiFormat("uri-reference");
        attribute.json().implementation(implementationClosure());
        return attribute;
    }

    /**
     * The {@code implementation { ... }} driver closure: when invoked with the JSON value
     * mapping builder as delegate, it registers the {@code deserialize} hook.
     */
    private static Closure<Object> implementationClosure() {
        return new Closure<Object>(CustomImplementationConnIdTypeTest.class) {
            public Object doCall(Object context) {
                var builder = (ValueMappingBuilder<Object, JsonNode>) getDelegate();
                builder.deserialize(deserializeClosure());
                return null;
            }
        };
    }

    /**
     * The {@code deserialize { ... }} hook: at runtime it is invoked with a
     * {@link ValueMappingBuilder.DeserializationContext} as delegate and converts the
     * wire JSON node ({@code {"href": ".../projects/42", "title": "..."}}) into a
     * {@link ConnectorObjectReference}.
     */
    private static Closure<Object> deserializeClosure() {
        return new Closure<Object>(CustomImplementationConnIdTypeTest.class) {
            public Object doCall(Object context) {
                var wireValue = ((ValueMappingBuilder.DeserializationContext<JsonNode>) getDelegate()).getValue();
                if (wireValue == null) {
                    return null;
                }
                var href = wireValue.get("href").asText();
                var uid = href.substring(href.lastIndexOf('/') + 1);
                var object = new ConnectorObjectBuilder()
                        .setObjectClass(new ObjectClass("Project"))
                        .setUid(uid)
                        .setName(wireValue.get("title").asText())
                        .build();
                return new ConnectorObjectReference(object);
            }
        };
    }

    private static JsonNode projectLink() {
        return new ObjectMapper().createObjectNode()
                .put("href", "/api/v1/projects/42")
                .put("title", "My Project");
    }

    @Test
    public void referenceAttribute_customImplementation_buildsSchema() {
        var definition = build(newReferenceAttribute());

        assertThat(definition.connId().getType()).isEqualTo(ConnectorObjectReference.class);
    }

    @Test
    public void referenceAttribute_customImplementation_mappingReportsConnIdSectionType() {
        var mapping = build(newReferenceAttribute()).json();

        assertThat(mapping.connIdType()).isEqualTo(ConnectorObjectReference.class);
    }

    @Test
    public void referenceAttribute_customImplementation_deserializesReference() {
        var mapping = build(newReferenceAttribute()).json();

        var value = mapping.singleValueFromAttribute(projectLink());

        assertThat(value).isInstanceOf(ConnectorObjectReference.class);
        var object = (ConnectorObject) ((ConnectorObjectReference) value).getValue();
        assertThat(object.getObjectClass()).isEqualTo(new ObjectClass("Project"));
        assertThat(object.getUid()).isEqualTo(new Uid("42"));
        assertThat(object.getName()).isEqualTo(new Name("My Project"));
    }

    @Test
    public void regularAttribute_customImplementation_nativeTypeUnchanged() {
        var attribute = newObjectClass().attribute("name");
        attribute.json().type("string");
        attribute.json().implementation(new Closure<Object>(CustomImplementationConnIdTypeTest.class) {
            public Object doCall(Object context) {
                var builder = (ValueMappingBuilder<Object, JsonNode>) getDelegate();
                builder.deserialize(new Closure<Object>(CustomImplementationConnIdTypeTest.class) {
                    public Object doCall(Object context) {
                        return ((ValueMappingBuilder.DeserializationContext<JsonNode>) getDelegate()).getValue().asText();
                    }
                });
                return null;
            }
        });

        var mapping = build(attribute).json();

        assertThat(mapping.connIdType()).isEqualTo(String.class);
        assertThat(mapping.singleValueFromAttribute(JsonNodeFactory.instance.textNode("abc"))).isEqualTo("abc");
    }

    @Test
    public void referenceAttribute_defaultImplementation_stillFailsUnsupportedCombination() {
        var attribute = newObjectClass().reference("project");
        attribute.objectClass("Project");
        attribute.json().type("string").openApiFormat("uri-reference");

        assertThatThrownBy(() -> build(attribute))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported override type combination");
    }

}
