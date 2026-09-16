/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.yaml.decl;

import com.evolveum.polygon.conndev.annotations.Script;
import com.evolveum.polygon.conndev.annotations.Yaml;
import com.evolveum.polygon.conndev.groovy.GroovyContext;
import com.evolveum.polygon.conndev.groovy.ScriptError;
import com.evolveum.polygon.conndev.yaml.GroovyScriptCompiler;
import groovy.lang.Closure;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;

import static org.testng.Assert.*;

/**
 * Exercises {@link GroovySyntaxChecker} against a small annotated toy builder — finds syntax
 * errors at any nesting depth, remaps line/column back onto the document, never executes a
 * fragment's body, stays silent on structural problems. Also covers the {@code
 * checkObjectClasses}/{@code checkOperations} envelope entry points.
 */
public class GroovySyntaxCheckerTest {

    static final class Inner {
        Closure<?> nestedScript;

        public Inner nestedScript(@Script.Runtime Closure<?> value) {
            this.nestedScript = value;
            return this;
        }
    }

    static final class Toy {
        String name;
        Closure<?> script;
        Inner inner;
        Map<String, Inner> byName;

        @Yaml.Key
        public Toy name(String value) {
            this.name = value;
            return this;
        }

        public Toy script(@Script.Runtime Closure<?> value) {
            this.script = value;
            return this;
        }

        @Yaml.Sub
        public Inner inner() {
            if (inner == null) {
                inner = new Inner();
            }
            return inner;
        }

        @Yaml.Map("byName")
        public Inner byName(String key) {
            return new Inner();
        }
    }

    private final GroovyScriptCompiler compiler = new GroovyScriptCompiler(new GroovyContext());

    private List<ScriptError> check(String yaml) {
        var document = LocatedDocument.parse("toy.yaml", yaml);
        return GroovySyntaxChecker.check(document, Toy.class, compiler);
    }

    @Test
    public void validFragmentsProduceNoErrors() {
        var errors = check("""
                name: Widget
                script: |
                  return 40 + 2
                inner:
                  nestedScript: |
                    return "ok"
                """);

        assertTrue(errors.isEmpty(), errors.toString());
    }

    @Test
    public void syntaxErrorInTopLevelFragmentIsReportedAtTheCorrectDocumentLine() {
        var errors = check("""
                name: Widget
                script: |
                  return 40 +
                  ) bad (
                """);

        assertEquals(errors.size(), 1, errors.toString());
        var error = errors.get(0);
        assertEquals(error.phase(), ScriptError.Phase.COMPILE);
        assertEquals(error.source(), "script");
        // Groovy reports "missing '}'" where the incomplete expression starts (the "return 40 +"
        // line), not at the unexpected ")" that follows it — document line 3, not 4.
        assertEquals((int) error.line(), 3, error.toString());
        // "  return 40 +" is indented 2 spaces; Groovy's own (unindented) column 12 (one past its
        // 11-character text) plus that indent lands at 14.
        assertEquals((int) error.column(), 14, error.toString());
    }

    @Test
    public void syntaxErrorInNestedFragmentIsReportedWithItsOwnPathAndLine() {
        var errors = check("""
                name: Widget
                inner:
                  nestedScript: |
                    return (
                """);

        assertEquals(errors.size(), 1, errors.toString());
        var error = errors.get(0);
        assertEquals(error.source(), "inner.nestedScript");
        assertEquals((int) error.line(), 4, error.toString());
        // This particular error (unterminated "(") is Groovy-reported past the fragment's real
        // content (the synthetic wrapper close), so the line is clamped and the column withheld —
        // a clamped position isn't a real document column.
        assertNull(error.column(), error.toString());
    }

    @Test
    public void columnOfANonClampedErrorIsIndentationAwareAtAnyNestingDepth() {
        var errors = check("""
                name: Widget
                inner:
                  nestedScript: |
                    return 1 +
                    ) bad (
                """);

        assertEquals(errors.size(), 1, errors.toString());
        var error = errors.get(0);
        assertEquals((int) error.line(), 4, error.toString());
        // Document line 4 is "    return 1 +" (4-space indent, one level deeper than a top-level
        // fragment): indent 4 + Groovy's own column 11 (one past its 10-character text) = 15.
        assertEquals((int) error.column(), 15, error.toString());
    }

    @Test
    public void columnIsUnreportedForAnInlineNonBlockFragment() {
        var errors = check("""
                name: Widget
                script: return 40 +
                """);

        assertEquals(errors.size(), 1, errors.toString());
        var error = errors.get(0);
        // Not a "key: |" block scalar, so there is no reliable indentation to add back — the line
        // is still reported (it's the key's own line here, not shifted like a block scalar's), but
        // the column is withheld.
        assertEquals((int) error.line(), 2, error.toString());
        assertNull(error.column(), error.toString());
    }

    @Test
    public void syntaxErrorInMapEntryFragmentIsReportedWithItsOwnPath() {
        var errors = check("""
                name: Widget
                byName:
                  first:
                    nestedScript: |
                      return (
                """);

        assertEquals(errors.size(), 1, errors.toString());
        assertEquals(errors.get(0).source(), "byName.first.nestedScript");
    }

    @Test
    public void checkerNeverExecutesAFragmentsBody() {
        var errors = check("""
                name: Widget
                script: |
                  throw new RuntimeException("must not run")
                """);

        assertTrue(errors.isEmpty(), errors.toString());
    }

    @Test
    public void unknownYamlKeyIsSilentlyIgnored() {
        var errors = check("name: Widget\nbogus: 1\n");

        assertTrue(errors.isEmpty(), errors.toString());
    }

    @Test
    public void wrongShapeForASubtreeIsSilentlyIgnored() {
        var errors = check("name: Widget\ninner: not-a-mapping\n");

        assertTrue(errors.isEmpty(), errors.toString());
    }

    private List<ScriptError> checkObjectClasses(String yaml) {
        var document = LocatedDocument.parse("schema.yaml", yaml);
        return GroovySyntaxChecker.checkObjectClasses(document, Toy.class, compiler);
    }

    @Test
    public void checkObjectClassesFindsSyntaxErrorsInEachEntryUnderItsOwnPath() {
        var errors = checkObjectClasses("""
                objectClasses:
                  User:
                    script: |
                      return (
                  Group:
                    name: Grp
                """);

        assertEquals(errors.size(), 1, errors.toString());
        assertEquals(errors.get(0).source(), "objectClasses.User.script");
    }

    @Test
    public void checkObjectClassesLeavesTheRelationshipsBlockUnwalked() {
        // "relationships" is not (yet) a @Yaml.* shape of any object-class builder, and is not
        // itself walked here — that block's own (unsupported) handling is YamlSchemaLoader's job.
        var errors = checkObjectClasses("""
                objectClasses:
                  User:
                    name: Widget
                relationships:
                  bogus: whatever
                """);

        assertTrue(errors.isEmpty(), errors.toString());
    }

    private List<ScriptError> checkOperations(String yaml) {
        var document = LocatedDocument.parse("operations.yaml", yaml);
        return GroovySyntaxChecker.checkOperations(document, Toy.class, Inner.class, compiler);
    }

    @Test
    public void checkOperationsFindsSyntaxErrorsInObjectClassesAndAuthentication() {
        var errors = checkOperations("""
                objectClasses:
                  User:
                    script: |
                      return (
                authentication:
                  nestedScript: |
                    return (
                """);

        assertEquals(errors.size(), 2, errors.toString());
        var sources = errors.stream().map(ScriptError::source).sorted().toList();
        assertEquals(sources, List.of("authentication.nestedScript", "objectClasses.User.script"));
    }

    @Test
    public void checkOperationsIgnoresUnknownTopLevelKeys() {
        var errors = checkOperations("bogus:\n  script: |\n    return (\n");

        assertTrue(errors.isEmpty(), errors.toString());
    }
}
