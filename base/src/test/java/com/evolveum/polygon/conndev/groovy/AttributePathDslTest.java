/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.groovy;

import com.evolveum.polygon.conndev.api.AttributePath;
import com.evolveum.polygon.conndev.api.ContextLookup;
import com.evolveum.polygon.conndev.api.ParsingException;
import com.evolveum.polygon.conndev.schema.BaseSchemaBuilder;
import org.codehaus.groovy.runtime.MethodClosure;
import org.identityconnectors.framework.spi.Configuration;
import org.identityconnectors.framework.spi.Connector;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests the {@code path { ... }} closure overload of the
 * {@link com.evolveum.polygon.conndev.build.api.AttributeBuilder.JsonMapping} DSL, exercising
 * the full Groovy path: shell closure delegate resolution (unqualified {@code JSON_PATH}/
 * {@code JSON_POINTER} constants) to the parsed {@link AttributePath}.
 */
public class AttributePathDslTest {

    private static final class StubConnector implements Connector {
        @Override public Configuration getConfiguration() { return null; }
        @Override public void init(Configuration c) {}
        @Override public void dispose() {}
    }

    private static final ContextLookup NOOP_CONTEXT = ContextLookup.none();

    private SchemaHarness harness() {
        var builder = new BaseSchemaBuilder(StubConnector.class, NOOP_CONTEXT);
        var context = new GroovyContext();
        var shell = context.createShell();
        shell.setVariable("objectClass", new MethodClosure(builder, "objectClass"));
        shell.setVariable("relationship", new MethodClosure(builder, "relationship"));
        return new SchemaHarness(builder, shell);
    }

    private AttributePath emailPathOf(String script) {
        return harness().loadInline(script).attribute("User", "email").json().path();
    }

    private static final AttributePath EMAILS_WORK_VALUE = new AttributePath(List.of(
            new AttributePath.Attribute("emails"),
            new AttributePath.SimpleValueFilter(Map.of("type", "work")),
            new AttributePath.Attribute("value")));

    private static final AttributePath USERS_0_EMAIL = new AttributePath(List.of(
            new AttributePath.Attribute("users"),
            new AttributePath.IndexFilter(0),
            new AttributePath.Attribute("email")));

    @Test
    public void testClosureDefaultFormatIsJsonPath() {
        var path = emailPathOf("""
                objectClass("User") {
                    attribute("email") {
                        json {
                            type "string"
                            path {
                                value '$.users[0].email'
                            }
                        }
                    }
                }
                """);
        assertThat(path).isEqualTo(USERS_0_EMAIL);
    }

    @Test
    public void testClosureExplicitJsonPathFormat() {
        var path = emailPathOf("""
                objectClass("User") {
                    attribute("email") {
                        json {
                            type "string"
                            path {
                                type JSON_PATH
                                value '$.users[0].email'
                            }
                        }
                    }
                }
                """);
        assertThat(path).isEqualTo(USERS_0_EMAIL);
    }

    @Test
    public void testClosureJsonPointerFormat() {
        var path = emailPathOf("""
                objectClass("User") {
                    attribute("email") {
                        json {
                            type "string"
                            path {
                                type JSON_POINTER
                                value "/users/0/email"
                            }
                        }
                    }
                }
                """);
        assertThat(path).isEqualTo(USERS_0_EMAIL);
    }
}
