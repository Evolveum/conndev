/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.yaml;

import com.evolveum.polygon.conndev.annotations.Yaml;
import com.evolveum.polygon.conndev.api.AttributePath;
import com.evolveum.polygon.conndev.api.AttributePathDeclaration;
import com.evolveum.polygon.conndev.api.AttributePathFormat;
import com.evolveum.polygon.conndev.api.BasicJsonPathFormat;
import com.evolveum.polygon.conndev.api.JsonPointerFormat;
import com.evolveum.polygon.conndev.groovy.GroovyContext;
import com.evolveum.polygon.conndev.yaml.decl.DeclYamlBinder;
import com.evolveum.polygon.conndev.yaml.decl.LocatedDocument;
import org.testng.annotations.Test;

import static org.testng.Assert.*;

/**
 * Exercises the {@code @Yaml.Path} binding of the generic {@link DeclYamlBinder} engine: a path
 * key binds a scalar expression (or a {@code {type, value}} mapping) into an
 * {@link AttributePathDeclaration} in the declared {@link AttributePathFormat}, carrying the key's
 * YAML source location, and fails fast on structural problems.
 */
public class YamlPathBindingTest {

    /** A format without the {@code public static final INSTANCE} convention (a binding misconfiguration). */
    static final class NoInstanceFormat implements AttributePathFormat<String> {
        @Override public AttributePath parse(String source) {
            throw new UnsupportedOperationException();
        }

        @Override public String serialize(AttributePath path) {
            throw new UnsupportedOperationException();
        }
    }

    static final class PathHolder {
        AttributePathDeclaration<?, ?> path;

        @Yaml.Path
        public PathHolder path(AttributePathDeclaration<?, ?> declaration) {
            this.path = declaration;
            return this;
        }
    }

    static final class JsonPointerHolder {
        AttributePathDeclaration<?, ?> path;

        @Yaml.Path(JsonPointerFormat.class)
        public JsonPointerHolder path(AttributePathDeclaration<?, ?> declaration) {
            this.path = declaration;
            return this;
        }
    }

    static final class NoInstanceHolder {
        @Yaml.Path(NoInstanceFormat.class)
        public NoInstanceHolder path(AttributePathDeclaration<?, ?> declaration) {
            throw new AssertionError("must not be reached");
        }
    }

    static final class WrongParamHolder {
        @Yaml.Path
        public WrongParamHolder path(String value) {
            throw new AssertionError("must not be reached");
        }
    }

    private static <T> T bind(Class<T> type, String yaml) throws Exception {
        var document = LocatedDocument.parse("toy.yaml", yaml);
        var binder = new DeclYamlBinder(document, new GroovyScriptCompiler(new GroovyContext()));
        var target = type.getDeclaredConstructor().newInstance();
        binder.bind(document.root(), target);
        return target;
    }

    private static IllegalArgumentException bindFails(Class<?> type, String yaml) throws Exception {
        return expectThrows(IllegalArgumentException.class, () -> bind(type, yaml));
    }

    private static void assertMessage(IllegalArgumentException exception, String fragment) {
        assertTrue(exception.getMessage().contains(fragment),
                "expected the message to contain '" + fragment + "': " + exception.getMessage());
    }

    @Test
    public void scalarExpressionUsesTheDefaultFormat() throws Exception {
        var holder = bind(PathHolder.class, "path: $.a.b[0]\n");

        assertEquals(holder.path.type().value(), BasicJsonPathFormat.INSTANCE);
        assertEquals(holder.path.value().value(), "$.a.b[0]");
        // the declaration carries the key's YAML source location
        assertEquals(holder.path.value().location().name(), "toy.yaml");
        assertEquals(holder.path.value().location().line(), 1);
        assertEquals(holder.path.value().location().column(), 1);
        // the expression parses to the expected path on first access
        assertEquals(holder.path.actual(), AttributePath.of("a").child("b").firstValue());
    }

    @Test
    public void mappingWithExplicitTypeOverridesTheDefault() throws Exception {
        var holder = bind(PathHolder.class, """
                path:
                  type: JSON_POINTER
                  value: /a/b/0
                """);

        assertEquals(holder.path.type().value(), JsonPointerFormat.INSTANCE);
        assertEquals(holder.path.value().value(), "/a/b/0");
        assertEquals(holder.path.actual(), AttributePath.of("a", "b").firstValue());
        assertEquals(holder.path.value().location().line(), 1);
    }

    @Test
    public void mappingWithoutTypeFallsBackToTheDefault() throws Exception {
        var holder = bind(JsonPointerHolder.class, """
                path:
                  value: /x/y
                """);

        // no explicit type: the binding's default (JsonPointer) applies
        assertEquals(holder.path.type().value(), JsonPointerFormat.INSTANCE);
        assertEquals(holder.path.actual(), AttributePath.of("x", "y"));
    }

    @Test
    public void typeNameIsCaseInsensitive() throws Exception {
        var holder = bind(PathHolder.class, """
                path:
                  type: json_path
                  value: $.x
                """);

        assertEquals(holder.path.type().value(), BasicJsonPathFormat.INSTANCE);
        assertEquals(holder.path.actual(), AttributePath.of("x"));
    }

    @Test
    public void unknownTypeNameFailsNamingSupportedNames() throws Exception {
        var exception = bindFails(PathHolder.class, """
                path:
                  type: REGEX
                  value: $.x
                """);

        assertMessage(exception, "Unknown path format 'REGEX'");
    }

    @Test
    public void unknownKeyInTheMappingFails() throws Exception {
        var exception = bindFails(PathHolder.class, """
                path:
                  value: $.x
                  format: JSON_PATH
                """);

        assertMessage(exception, "unknown key 'format'");
    }

    @Test
    public void missingValueFails() throws Exception {
        var exception = bindFails(PathHolder.class, """
                path:
                  type: JSON_PATH
                """);

        assertMessage(exception, "requires a 'value'");
    }

    @Test
    public void blankValueFails() throws Exception {
        var exception = bindFails(PathHolder.class, "path: \"   \"\n");

        assertMessage(exception, "must not be blank");
    }

    @Test
    public void listValueFails() throws Exception {
        var exception = bindFails(PathHolder.class, """
                path:
                  - $.a
                  - $.b
                """);

        assertMessage(exception, "got a list");
    }

    @Test
    public void nullValueLeavesTheBuilderUntouched() throws Exception {
        var holder = bind(PathHolder.class, "path: null\n");

        assertNull(holder.path);
    }

    @Test
    public void formatWithoutInstanceFails() throws Exception {
        var exception = expectThrows(IllegalStateException.class, () -> bind(NoInstanceHolder.class, "path: x\n"));

        assertTrue(exception.getMessage().contains("INSTANCE"), exception.getMessage());
    }

    @Test
    public void nonDeclarationParameterFails() throws Exception {
        var exception = expectThrows(IllegalStateException.class, () -> bind(WrongParamHolder.class, "path: x\n"));

        assertTrue(exception.getMessage().contains("AttributePathDeclaration"), exception.getMessage());
    }
}
