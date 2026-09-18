/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.groovy;

import com.evolveum.polygon.conndev.api.ContextLookup;
import com.evolveum.polygon.conndev.concepts.RetrievableContext;
import com.evolveum.polygon.conndev.schema.BaseAttributeDefinition;
import com.evolveum.polygon.conndev.schema.BaseObjectClassDefinition;
import com.evolveum.polygon.conndev.schema.BaseSchema;
import com.evolveum.polygon.conndev.schema.BaseSchemaBuilder;
import com.evolveum.polygon.conndev.spi.ObjectClassHandler;
import org.codehaus.groovy.runtime.MethodClosure;
import org.identityconnectors.framework.common.exceptions.ConfigurationException;
import org.identityconnectors.framework.common.objects.AttributeBuilder;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.filter.FilterBuilder;
import org.identityconnectors.framework.spi.Configuration;
import org.identityconnectors.framework.spi.Connector;
import org.testng.Assert;
import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies build-phase validation of attribute names referenced from search filter
 * specifications, custom search scripts and attribute resolvers: an unknown attribute
 * name must fail fast with a {@link ConfigurationException} that names the attribute,
 * the object class and the available attributes, instead of surfacing later as a
 * {@code NullPointerException}.
 */
public class SearchFilterAttributeValidationTest {

    private static final String SCHEMA_SCRIPT = """
            objectClass("User") {
                attribute("organization_id") {
                    jsonType "string"
                    connId {
                        name "organizationId"
                        type String
                    }
                }
                attribute("login") {
                    jsonType "string"
                }
            }
            """;

    private static final class StubConnector implements Connector {
        @Override public Configuration getConfiguration() { return null; }
        @Override public void init(Configuration c) {}
        @Override public void dispose() {}
    }

    private static final class StubConnectorContext implements ConnectorContext {
        @Override public ObjectClassHandler handlerFor(ObjectClass objectClass) {
            throw new UnsupportedOperationException("Not supported in test");
        }

        @Override public BaseSchema schema() {
            throw new UnsupportedOperationException("Not supported in test");
        }

        @Override public boolean getDevelopmentMode() {
            return false;
        }

        @Override public <T extends RetrievableContext> T getUnchecked(Class<T> contextType) {
            return null;
        }
    }

    private SchemaHarness schema() {
        var builder = new BaseSchemaBuilder(StubConnector.class, ContextLookup.none());
        var context = new GroovyContext();
        var shell = context.createShell();
        shell.setVariable("objectClass", new MethodClosure(builder, "objectClass"));
        shell.setVariable("relationship", new MethodClosure(builder, "relationship"));
        return new SchemaHarness(builder, shell).loadInline(SCHEMA_SCRIPT);
    }

    private BaseObjectClassDefinition<BaseAttributeDefinition> user() {
        return schema().objectClass("User");
    }

    @Test
    public void requireAttributeWithUnknownNameFailsWithClearMessage() {
        var exception = Assert.expectThrows(ConfigurationException.class,
                () -> user().requireAttribute("organization", "when defining a search filter"));

        assertThat(exception.getMessage())
                .contains("Attribute 'organization' not found in object class 'User'")
                .contains("when defining a search filter")
                .contains("organization_id")
                .contains("login");
    }

    @Test
    public void requireAttributeWithKnownNameResolves() {
        var attribute = user().requireAttribute("organization_id", "when defining a search filter");
        assertThat(attribute).isNotNull();
        assertThat(attribute.remoteName()).isEqualTo("organization_id");
    }

    @Test
    public void filterAttributeIsKeyedByConnIdName() {
        var spec = user().filterAttribute("organization_id", "when defining a search filter");

        assertThat(spec.matches(FilterBuilder.equalTo(AttributeBuilder.build("organizationId", "acme")))).isTrue();
        assertThat(spec.matches(FilterBuilder.equalTo(AttributeBuilder.build("organization_id", "acme")))).isFalse();
    }

    @Test
    public void filterAttributeWithUnknownNameFailsWithClearMessage() {
        assertThatThrownBy(() -> user().filterAttribute("organization", "when defining a search filter"))
                .isInstanceOf(ConfigurationException.class)
                .hasMessageContaining("Attribute 'organization' not found in object class 'User'")
                .hasMessageContaining("Available attributes");
    }

    @Test
    public void searchScriptBuilderAttributeWithUnknownNameFails() {
        var builder = new GroovySearchScriptBuilder(new StubConnectorContext(), user());

        assertThatThrownBy(() -> builder.attribute("organization"))
                .isInstanceOf(ConfigurationException.class)
                .hasMessageContaining("Attribute 'organization' not found in object class 'User'")
                .hasMessageContaining("when defining a custom search filter")
                .hasMessageContaining("organization_id");
    }

    @Test
    public void searchScriptBuilderAttributeWithKnownNameIsKeyedByConnIdName() {
        var builder = new GroovySearchScriptBuilder(new StubConnectorContext(), user());

        var spec = builder.attribute("organization_id");

        assertThat(spec.matches(FilterBuilder.equalTo(AttributeBuilder.build("organizationId", "acme")))).isTrue();
    }

    @Test
    public void attributeResolverBuilderWithUnknownNameFails() {
        var builder = new ScriptedAttributeResolverBuilder(new StubConnectorContext(), user());

        assertThatThrownBy(() -> builder.attribute("nonexistent"))
                .isInstanceOf(ConfigurationException.class)
                .hasMessageContaining("Attribute 'nonexistent' not found in object class 'User'")
                .hasMessageContaining("when defining an attribute resolver");
    }
}
