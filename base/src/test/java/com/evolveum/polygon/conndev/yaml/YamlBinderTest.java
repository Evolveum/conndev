/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.yaml;

import com.evolveum.polygon.conndev.annotations.Script;
import com.evolveum.polygon.conndev.annotations.Yaml;
import com.evolveum.polygon.conndev.concepts.DefinitionValue;
import com.evolveum.polygon.conndev.groovy.GroovyContext;
import com.evolveum.polygon.conndev.yaml.decl.DeclYamlBinder;
import com.evolveum.polygon.conndev.yaml.decl.LocatedDocument;
import groovy.lang.Closure;
import org.testng.annotations.Test;

import static org.testng.Assert.*;

/**
 * Exercises the generic {@link DeclYamlBinder} engine against a small annotated toy builder — proving
 * leaf binding (with {@code DefinitionValue} preference and YAML location), sub-builder recursion,
 * block-scalar closures, enum/numeric coercion, null handling and fail-fast on unknown keys — before
 * it is wired onto the real schema builders.
 */
public class YamlBinderTest {

    enum Color {
        RED, GREEN, BLUE
    }

    static final class Inner {
        String label;

        @Yaml.Key
        public Inner label(String value) {
            this.label = value;
            return this;
        }
    }

    static final class Toy {
        String name;
        DefinitionValue<Boolean> activeDv;   // set only by the DV overload
        boolean activePlain;                 // set only by the plain overload (must stay false)
        boolean plainFlag;
        Double weight;
        Color color;
        Inner inner;
        Closure<?> script;

        @Yaml.Key
        public Toy name(String value) {
            this.name = value;
            return this;
        }

        @Yaml.Key
        public Toy active(boolean value) {
            this.activePlain = value; // plain — must NOT be called when a DV overload exists
            return this;
        }

        public Toy active(DefinitionValue<Boolean> value) {
            this.activeDv = value; // DV overload — preferred by the binder
            return this;
        }

        @Yaml.Key
        public Toy plainFlag(boolean value) {
            this.plainFlag = value;
            return this;
        }

        @Yaml.Key
        public Toy weight(double value) {
            this.weight = value;
            return this;
        }

        @Yaml.Key
        public Toy color(Color value) {
            this.color = value;
            return this;
        }

        @Yaml.Sub
        public Inner inner() {
            if (inner == null) {
                inner = new Inner();
            }
            return inner;
        }

        public Toy script(@Script.Runtime Closure<?> closure) {
            this.script = closure;
            return this;
        }
    }

    private final GroovyScriptCompiler compiler = new GroovyScriptCompiler(new GroovyContext());

    private Toy bind(String yaml) {
        var document = LocatedDocument.parse("toy.yaml", yaml);
        var binder = new DeclYamlBinder(document, compiler);
        var toy = new Toy();
        binder.bind(document.root(), toy);
        return toy;
    }

    @Test
    public void bindsLeavesSubBuilderClosureAndPrefersDefinitionValue() {
        var toy = bind("""
                name: Widget
                active: true
                plainFlag: false
                weight: 1.5
                color: GREEN
                inner:
                  label: hello
                script: |
                  return 40 + 2
                """);

        assertEquals(toy.name, "Widget");
        // DV preference: the DefinitionValue overload was used, carrying the YAML source + line/col
        assertNotNull(toy.activeDv);
        assertEquals(toy.activeDv.value(), Boolean.TRUE);
        assertEquals(toy.activeDv.location().name(), "toy.yaml");
        assertEquals(toy.activeDv.location().line(), 2);
        assertEquals(toy.activeDv.location().column(), 1);
        assertFalse(toy.activePlain, "the plain overload must not be called when a DV overload exists");
        assertFalse(toy.plainFlag);
        assertEquals(toy.weight, 1.5);
        assertEquals(toy.color, Color.GREEN);
        assertNotNull(toy.inner);
        assertEquals(toy.inner.label, "hello");
        assertNotNull(toy.script);
        assertEquals(toy.script.call(), 42);
    }

    @Test
    public void unknownKeyFailsFastNamingKeyAndLocation() {
        var document = LocatedDocument.parse("toy.yaml", "name: Widget\nbogus: 1\n");
        var binder = new DeclYamlBinder(document, compiler);

        IllegalArgumentException exception;
        try {
            binder.bind(document.root(), new Toy());
            fail("expected IllegalArgumentException for the unknown key");
            exception = null;
        } catch (IllegalArgumentException e) {
            exception = e;
        }

        assertTrue(exception.getMessage().contains("bogus"), exception.getMessage());
        assertTrue(exception.getMessage().contains("toy.yaml:2"), exception.getMessage());
    }

    @Test
    public void nullValueLeavesDefaultUntouched() {
        var toy = bind("name:\nplainFlag: false\n");

        assertNull(toy.name);
        assertFalse(toy.plainFlag);
    }
}
