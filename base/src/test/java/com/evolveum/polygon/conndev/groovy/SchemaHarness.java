package com.evolveum.polygon.conndev.groovy;

import com.evolveum.polygon.conndev.schema.BaseAttributeDefinition;
import com.evolveum.polygon.conndev.schema.BaseObjectClassDefinition;
import com.evolveum.polygon.conndev.schema.BaseSchema;
import com.evolveum.polygon.conndev.schema.BaseSchemaBuilder;
import groovy.lang.GroovyShell;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Harness for schema parsing tests, providing fluent access to builders and
 * convenient assertion helpers. Also serves as the source of truth for building
 * the final {@link BaseSchema}.
 */
final class SchemaHarness {

    private final BaseSchemaBuilder builder;
    private final GroovyShell shell;
    private final java.util.function.Function<String, String> resourceLoader;
    private BaseSchema cachedSchema;

    SchemaHarness(BaseSchemaBuilder builder, GroovyShell shell) {
        this.builder = builder;
        this.shell = shell;
        this.resourceLoader = this::readResource;
    }
    /**
     * Reads a resource from classpath as a string.
     */
    private String readResource(String resourcePath) {
        try (var is = this.getClass().getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new IllegalArgumentException("Resource not found: " + resourcePath);
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to read resource: " + resourcePath, e);
        }
    }

    /**
     * @return the built {@link BaseSchema} (cached after first call)
     */
    public BaseSchema build() {
        if (cachedSchema == null) {
            cachedSchema = builder.build();
        }
        return cachedSchema;
    }

    /**
     * @return the object class defined by {@code name}
     */
    public BaseObjectClassDefinition<BaseAttributeDefinition> objectClass(String name) {
        return build().objectClass(name);
    }

    /**
     * @return the attribute by protocol name on object class {@code name}
     */
    public BaseAttributeDefinition attribute(String objectClass, String attribute) {
        return objectClass(objectClass).attributeFromProtocolName(attribute);
    }

    /**
     * @return the GroovyShell for direct script evaluation (e.g. AssertJ exception matchers)
     */
    public GroovyShell shell() {
        return shell;
    }

    /**
     * Loads a resource into the same builder. Use {@link #build()} after all scripts are loaded.
     * Also aliased as {@link #loadInline(String)} for chainable method calls.
     */
    public SchemaHarness loadInline(String script) {
        shell.evaluate(script);
        return this;
    }

    public SchemaHarness loadFromResource(String resourcePath) {
        shell.evaluate(readResource(resourcePath));
        return this;
    }
}
