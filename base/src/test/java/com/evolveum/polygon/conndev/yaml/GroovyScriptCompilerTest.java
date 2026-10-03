/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.yaml;

import com.evolveum.polygon.conndev.groovy.GroovyContext;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;

/**
 * The bridge between declarative YAML and imperative logic: Groovy block scalars compile to
 * closures that behave exactly like closures written in the Groovy DSL.
 */
public class GroovyScriptCompilerTest {

    private final GroovyScriptCompiler compiler = new GroovyScriptCompiler(new GroovyContext());

    @Test
    public void compilesZeroArgClosure() {
        var closure = compiler.compile("return 40 + 2");

        assertEquals(closure.call(), 42);
    }

    @Test
    public void compilesSingleParameterClosure() {
        var closure = compiler.compile("return request * 2", "request");

        assertEquals(closure.call(21), 42);
    }

    @Test
    public void evaluatesBuildTimeExpressionAgainstDelegate() {
        // the delegate plays the role of a builder (e.g. supportedFilter spec evaluation)
        var result = compiler.evaluate("attribute(\"id\")", new Object() {
            @SuppressWarnings("unused")
            public String attribute(String name) {
                return "attr:" + name;
            }
        });

        assertEquals(result, "attr:id");
    }

    @Test
    public void hoistsLeadingImportOutOfTheClosure() {
        // imports are part of the Groovy script, not the YAML envelope — the fragment carries them
        var closure = compiler.compile("import java.math.BigInteger\n"
                + "var big = new BigInteger(\"41\")\n"
                + "return big + 1");

        assertEquals(closure.call(), new java.math.BigInteger("42"));
    }

    @Test
    public void hoistsMultipleImportsIncludingStarAndSemicolon() {
        var closure = compiler.compile("import java.util.List;\n"
                + "import java.math.*\n"
                + "return new ArrayList<>(List.of(new BigDecimal(\"1\"))).size() + (int) new BigInteger(\"2\").longValue()");

        assertEquals(closure.call(), 3);
    }

    @Test
    public void hoistsImportsOfAParameterizedClosure() {
        var closure = compiler.compile("import java.math.BigInteger\n"
                + "return value * 2", "value");

        assertEquals(closure.call(new java.math.BigInteger("21")), new java.math.BigInteger("42"));
    }

    @Test
    public void nonImportLeadingLineIsNotHoisted() {
        // only lines that are import statements get hoisted — 'imported = 1' is an assignment,
        // so the fragment must compile unhoisted
        var closure = compiler.compile("imported = 1\nreturn imported + 1");

        assertEquals(closure.call(), 2);
    }
}
