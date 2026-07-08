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
}
