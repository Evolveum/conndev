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
import com.evolveum.polygon.conndev.groovy.ScriptError;
import com.evolveum.polygon.conndev.yaml.decl.DeclYamlBinder;
import com.evolveum.polygon.conndev.yaml.decl.GroovySyntaxChecker;
import com.evolveum.polygon.conndev.yaml.decl.LocatedDocument;
import groovy.lang.Closure;
import org.testng.annotations.Test;

import java.util.List;
import java.util.function.Function;

import static org.testng.Assert.*;

/**
 * Exercises the {@code @Yaml.Shortcut} leaf bindings — constant names resolving to the constant's
 * value (declared on a super-interface of the target), literal values still coercing as usual,
 * the Groovy-block (closure) fallback form, the {@code DefinitionValue} preference, fail-fast on
 * misnamed or mistyped constants, and the syntax checker treating a shortcut name as a constant
 * while checking a non-matching block as Groovy.
 */
public class YamlShortcutBindingTest {

    interface FormatConstants {
        String FMT_A = "value-a";
        String FMT_B = "value-b";
        Class<?> STRING_CLASS = String.class;
        Function<Object, String> UPPER = s -> s.toString().toUpperCase();
    }

    static final class ShortcutToy implements FormatConstants {
        String format;
        DefinitionValue<String> formatDv;   // set only by the DV overload
        Class<?> kind;
        Function<Object, String> form;
        Closure<?> formScript;

        @Yaml.Key
        @Yaml.Shortcut({"FMT_A", "FMT_B"})
        public ShortcutToy format(String value) {
            this.format = value; // plain — must NOT be called when a DV overload exists
            return this;
        }

        public ShortcutToy format(DefinitionValue<String> value) {
            this.formatDv = value; // DV overload — preferred by the binder
            return this;
        }

        @Yaml.Key
        @Yaml.Shortcut({"STRING_CLASS"})
        public ShortcutToy kind(Class<?> value) {
            this.kind = value;
            return this;
        }

        @Yaml.Key
        @Yaml.Shortcut({"UPPER"})
        public ShortcutToy form(Function<Object, String> value) {
            this.form = value;
            return this;
        }

        public ShortcutToy form(@Script.Runtime Closure<?> closure) {
            this.formScript = closure;
            return this;
        }
    }

    static final class MissingConstant {
        @Yaml.Key
        @Yaml.Shortcut({"NO_SUCH_CONSTANT"})
        public MissingConstant value(String value) {
            return this;
        }
    }

    static final class MistypedConstant implements FormatConstants {
        @Yaml.Key
        @Yaml.Shortcut({"STRING_CLASS"})
        public MistypedConstant value(String value) {
            return this;
        }
    }

    static final class NotAConstant {
        public String NOT_A_CONSTANT = "x";

        @Yaml.Key
        @Yaml.Shortcut({"NOT_A_CONSTANT"})
        public NotAConstant value(String value) {
            return this;
        }
    }

    enum Kind { A, B }

    interface KindConstants {
        Kind A = Kind.A;
        Kind B = Kind.B;
    }

    /**
     * A leaf {@code name(String)} whose shortcut constants are of a *different* type ({@code Kind});
     * a same-named {@code name(Kind)} overload performs the conversion. Mirrors the ConnId
     * {@code connId.name} shape (keyword {@code UID} → {@code name(ConnIdBuiltInAttribute)}).
     */
    static final class ShortcutOverloadToy implements KindConstants {
        String name;    // set only by the plain String leaf
        Kind kind;      // set only by the enum overload (the conversion target)

        @Yaml.Key
        @Yaml.Shortcut({"A", "B"})
        public ShortcutOverloadToy name(String value) {
            this.name = value;
            return this;
        }

        public ShortcutOverloadToy name(Kind value) {
            this.kind = value;
            return this;
        }
    }

    private final GroovyScriptCompiler compiler = new GroovyScriptCompiler(new GroovyContext());

    private ShortcutToy bind(String yaml) {
        var document = LocatedDocument.parse("toy.yaml", yaml);
        var binder = new DeclYamlBinder(document, compiler);
        var toy = new ShortcutToy();
        binder.bind(document.root(), toy);
        return toy;
    }

    private ShortcutOverloadToy bindOverload(String yaml) {
        var document = LocatedDocument.parse("toy.yaml", yaml);
        var binder = new DeclYamlBinder(document, compiler);
        var toy = new ShortcutOverloadToy();
        binder.bind(document.root(), toy);
        return toy;
    }

    @Test
    public void shortcutNameIsResolvedToTheConstantValue() {
        var toy = bind("format: FMT_A\nkind: STRING_CLASS\n");

        // The shortcut flows through the preferred DefinitionValue overload, with the YAML location
        assertNotNull(toy.formatDv, "the DV overload must be used");
        assertEquals(toy.formatDv.value(), "value-a");
        assertEquals(toy.formatDv.location().name(), "toy.yaml");
        assertNull(toy.format, "the plain overload must not be called when a DV overload exists");
        assertEquals(toy.kind, String.class);
    }

    @Test
    public void nonShortcutValueCoercesAsUsual() {
        var toy = bind("format: custom\nkind: STRING_CLASS\n");

        assertEquals(toy.formatDv.value(), "custom", "a non-matching scalar must pass through the usual coercion");
        assertEquals(toy.kind, String.class);
    }

    @Test
    public void nullValueLeavesDefaultUntouched() {
        var toy = bind("format:\nkind:\n");

        assertNull(toy.formatDv);
        assertNull(toy.kind);
    }

    @Test
    public void shortcutConstantIsPassedToTheLeafMethod() {
        var toy = bind("form: UPPER\n");

        assertNotNull(toy.form, "the shortcut constant must be bound onto the leaf method");
        assertEquals(toy.form.apply("x"), "X");
        assertNull(toy.formScript, "the closure form must not be used for a shortcut name");
    }

    @Test
    public void mismatchedTypeConstantRoutesToTheSameNamedOverload() {
        var toy = bindOverload("name: A\n");

        assertEquals(toy.kind, Kind.A, "the enum constant must be routed to the same-named enum overload");
        assertNull(toy.name, "the plain String leaf must not be called for a mismatched-type constant");
    }

    @Test
    public void nonShortcutLiteralStillReachesThePlainLeaf() {
        var toy = bindOverload("name: custom\n");

        assertEquals(toy.name, "custom", "a non-shortcut literal must still be coerced to the String leaf");
        assertNull(toy.kind, "the enum overload must not be called for a literal");
    }

    @Test
    public void mismatchedTypeConstantWithoutAnOverloadFailsFast() {
        var document = LocatedDocument.parse("toy.yaml", "name: A\n");
        var binder = new DeclYamlBinder(document, compiler);

        IllegalStateException exception;
        try {
            binder.bind(document.root(), new NoOverloadToy());
            fail("expected IllegalStateException for a mismatched-type constant with no same-named overload");
            exception = null;
        } catch (IllegalStateException e) {
            exception = e;
        }

        assertTrue(exception.getMessage().contains("A"), exception.getMessage());
        assertTrue(exception.getMessage().contains(Kind.class.getName()), exception.getMessage());
        assertTrue(exception.getMessage().contains("java.lang.String"), exception.getMessage());
    }

    /** A {@code name(String)} leaf with an enum shortcut constant but no {@code name(Kind)} overload. */
    static final class NoOverloadToy implements KindConstants {
        String name;

        @Yaml.Key
        @Yaml.Shortcut({"A", "B"})
        public NoOverloadToy name(String value) {
            this.name = value;
            return this;
        }
    }

    @Test
    public void nonMatchingScalarFallsBackToTheClosureForm() {
        var toy = bind("form: |\n  return 40 + 2\n");

        assertNull(toy.form, "the leaf method must not be used for a non-matching scalar");
        assertNotNull(toy.formScript, "the Groovy block must be compiled to the closure form");
        assertEquals(toy.formScript.call(), 42);
    }

    @Test
    public void nonScalarFailsWhenTheClosureFormExists() {
        var document = LocatedDocument.parse("toy.yaml", "form:\n  a: b\n");
        var binder = new DeclYamlBinder(document, compiler);

        IllegalArgumentException exception;
        try {
            binder.bind(document.root(), new ShortcutToy());
            fail("expected IllegalArgumentException for a non-scalar value of a shortcut key");
            exception = null;
        } catch (IllegalArgumentException e) {
            exception = e;
        }

        assertTrue(exception.getMessage().contains("form"), exception.getMessage());
        assertTrue(exception.getMessage().contains("UPPER"), exception.getMessage());
    }

    @Test
    public void nonMatchingValueOfNonPassThroughTypeFailsNamingTheShortcuts() {
        var document = LocatedDocument.parse("toy.yaml", "kind: BOGUS\n");
        var binder = new DeclYamlBinder(document, compiler);

        IllegalArgumentException exception;
        try {
            binder.bind(document.root(), new ShortcutToy());
            fail("expected IllegalArgumentException for a non-coercible value of a shortcut key");
            exception = null;
        } catch (IllegalArgumentException e) {
            exception = e;
        }

        assertTrue(exception.getMessage().contains("BOGUS"), exception.getMessage());
        assertTrue(exception.getMessage().contains("STRING_CLASS"), exception.getMessage());
        assertTrue(exception.getMessage().contains("toy.yaml"), exception.getMessage());
    }

    @Test
    public void missingShortcutConstantFailsFastNamingConstantAndType() {
        var document = LocatedDocument.parse("toy.yaml", "value: x\n");
        var binder = new DeclYamlBinder(document, compiler);

        IllegalStateException exception;
        try {
            binder.bind(document.root(), new MissingConstant());
            fail("expected IllegalStateException for a missing shortcut constant");
            exception = null;
        } catch (IllegalStateException e) {
            exception = e;
        }

        assertTrue(exception.getMessage().contains("NO_SUCH_CONSTANT"), exception.getMessage());
        assertTrue(exception.getMessage().contains(MissingConstant.class.getName()), exception.getMessage());
    }

    @Test
    public void mistypedShortcutConstantFailsFastNamingTheTypes() {
        var document = LocatedDocument.parse("toy.yaml", "value: x\n");
        var binder = new DeclYamlBinder(document, compiler);

        IllegalStateException exception;
        try {
            binder.bind(document.root(), new MistypedConstant());
            fail("expected IllegalStateException for a mistyped shortcut constant");
            exception = null;
        } catch (IllegalStateException e) {
            exception = e;
        }

        assertTrue(exception.getMessage().contains("java.lang.Class"), exception.getMessage());
        assertTrue(exception.getMessage().contains("java.lang.String"), exception.getMessage());
    }

    @Test
    public void nonFinalShortcutFieldFailsFast() {
        var document = LocatedDocument.parse("toy.yaml", "value: x\n");
        var binder = new DeclYamlBinder(document, compiler);

        IllegalStateException exception;
        try {
            binder.bind(document.root(), new NotAConstant());
            fail("expected IllegalStateException for a non-final shortcut field");
            exception = null;
        } catch (IllegalStateException e) {
            exception = e;
        }

        assertTrue(exception.getMessage().contains("NOT_A_CONSTANT"), exception.getMessage());
        assertTrue(exception.getMessage().contains("static final"), exception.getMessage());
    }

    @Test
    public void syntaxCheckerChecksTheGroovyBlockOfAShortcutKey() {
        var document = LocatedDocument.parse("toy.yaml", """
                form: |
                  return oops(
                """);

        List<ScriptError> errors = GroovySyntaxChecker.check(document, ShortcutToy.class, compiler);

        assertEquals(errors.size(), 1, errors.toString());
        assertEquals(errors.getFirst().source(), "form");
    }

    @Test
    public void syntaxCheckerDoesNotCheckAShortcutNameAsGroovy() {
        var document = LocatedDocument.parse("toy.yaml", """
                format: FMT_A
                form: UPPER
                """);

        List<ScriptError> errors = GroovySyntaxChecker.check(document, ShortcutToy.class, compiler);

        assertTrue(errors.isEmpty(), errors.toString());
    }
}
