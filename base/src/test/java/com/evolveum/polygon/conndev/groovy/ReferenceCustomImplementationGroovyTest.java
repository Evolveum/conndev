/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.groovy;

import com.evolveum.polygon.conndev.api.ContextLookup;
import com.evolveum.polygon.conndev.json.JsonAttributeMapping;
import com.evolveum.polygon.conndev.schema.BaseSchema;
import com.evolveum.polygon.conndev.schema.BaseSchemaBuilder;
import com.evolveum.polygon.conndev.schema.StubConnector;
import org.codehaus.groovy.runtime.MethodClosure;
import org.identityconnectors.framework.common.objects.AttributeBuilder;
import org.identityconnectors.framework.common.objects.ConnectorObject;
import org.identityconnectors.framework.common.objects.ConnectorObjectReference;
import org.identityconnectors.framework.common.objects.Name;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.Uid;
import org.testng.annotations.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.JsonNodeFactory;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Reproduces support bug #12502 with the exact Groovy DSL from the report: an
 * OpenProject membership with a reference attribute over a string JSON backing
 * ({@code _links.project}) whose custom {@code implementation { deserialize { ... } }}
 * builds a {@link ConnectorObjectReference} from the wire value. Before the fix the
 * schema build failed with "Unsupported override type combination" — a custom
 * implementation is now re-labeled to the connId section type instead of being
 * bridged against the default JSON mapping's native type.
 */
public class ReferenceCustomImplementationGroovyTest {

    private static final String SCHEMA = """
            import org.identityconnectors.framework.common.objects.ConnectorObjectBuilder
            import org.identityconnectors.framework.common.objects.ConnectorObjectReference
            import org.identityconnectors.framework.common.objects.ObjectClass

            objectClass("Membership") {
                reference("project") {
                    objectClass "Project"
                    json {
                        type ("string")
                        openApiFormat ("uri-reference")
                        path attribute("_links").child("project")
                        implementation {
                            deserialize {

                                if(it == null){
                                    it = value
                                }
                                var href = it.get("href")?.asText()
                                var pid = href.substring(href.lastIndexOf("/") + 1)

                                var obj = new ConnectorObjectBuilder()
                                        .setObjectClass(new ObjectClass("Project"))
                                        .setUid(pid)
                                        .setName(it.get("title")?.asText())
                                return new ConnectorObjectReference(obj.build())
                            }
                        }
                    }
                }
            }
            """;

    private static BaseSchema buildSchema() {
        var builder = new BaseSchemaBuilder(StubConnector.class, ContextLookup.none());
        var shell = new GroovyContext().createShell();
        shell.setVariable("objectClass", new MethodClosure(builder, "objectClass"));
        shell.evaluate(SCHEMA);
        builder.applyStructuralRules();
        return builder.build();
    }

    private static JsonAttributeMapping projectMapping() {
        return buildSchema()
                .objectClass("Membership")
                .attributeFromProtocolName("project")
                .json();
    }

    @Test
    public void membershipProjectReference_buildsSchema() {
        var project = buildSchema().objectClass("Membership").attributeFromProtocolName("project");

        assertThat(project).isNotNull();
        assertThat(project.connId().getType()).isEqualTo(ConnectorObjectReference.class);
    }

    @Test
    public void membershipProjectReference_mappingReportsConnIdSectionType() {
        assertThat(projectMapping().connIdType()).isEqualTo(ConnectorObjectReference.class);
    }

    @Test
    public void membershipProjectReference_deserializesProjectLink() {
        var mapping = projectMapping();

        var object = new ObjectMapper().createObjectNode();
        object.putObject("_links")
                .putObject("project")
                .put("href", "/api/v1/projects/42")
                .put("title", "My Project");

        List<Object> values = mapping.valuesFromObject(object);

        assertThat(values).hasSize(1);
        assertThat(values.getFirst()).isInstanceOf(ConnectorObjectReference.class);
        var connectorObject = (ConnectorObject) ((ConnectorObjectReference) values.getFirst()).getValue();
        assertThat(connectorObject.getObjectClass()).isEqualTo(new ObjectClass("Project"));
        assertThat(connectorObject.getUid()).isEqualTo(new Uid("42"));
        assertThat(connectorObject.getName()).isEqualTo(new Name("My Project"));
    }

    @Test
    public void membershipProjectReference_nullLinkYieldsNoValue() {
        var mapping = projectMapping();

        var object = new ObjectMapper().createObjectNode();
        object.putObject("_links");

        assertThat(mapping.valuesFromObject(object)).isNull();
    }

    /**
     * The Groovy DSL's {@code implementation { serialize { ... } }} direction runs through the
     * same shared value-mapping sub-builder as the declarative YAML block: the closure's result
     * is encoded by the base mapping derived from the declared JSON type.
     */
    @Test
    public void groovyDslSerializeDirection_buildsThroughTheSharedSubBuilder() {
        var builder = new BaseSchemaBuilder(StubConnector.class, ContextLookup.none());
        var shell = new GroovyContext().createShell();
        shell.setVariable("objectClass", new MethodClosure(builder, "objectClass"));
        shell.evaluate("""
                objectClass("Widget") {
                    attribute("label") {
                        json {
                            type("string")
                            implementation {
                                serialize {
                                    return "s:" + value
                                }
                            }
                        }
                    }
                }
                """);
        builder.applyStructuralRules();

        var mapping = builder.build().objectClass("Widget").attributeFromProtocolName("label").json();

        var parent = JsonNodeFactory.instance.objectNode();
        mapping.toJsonNode(AttributeBuilder.build("label", "x"), parent);

        assertThat(parent.get("label").asText()).isEqualTo("s:x");
    }

}
