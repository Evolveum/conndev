/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.yaml;

import com.evolveum.polygon.conndev.groovy.GroovyContext;
import groovy.lang.Closure;

/**
 * Compiles YAML block scalars carrying Groovy source (e.g. {@code implementation: |}) into
 * {@link Closure}s, so imperative logic keeps working under the declarative YAML envelope.
 *
 * <p>The snippet is wrapped in a closure literal and evaluated on the shared {@link GroovyContext}
 * shell. The delegate and resolve strategy are set later by the builders, exactly as for a closure
 * written in the Groovy DSL — so the runtime context and its members resolve identically.
 */
public final class GroovyScriptCompiler {

    private final GroovyContext groovyContext;

    public GroovyScriptCompiler(GroovyContext groovyContext) {
        this.groovyContext = groovyContext;
    }

    public Closure<?> compile(String groovySource) {
        return (Closure<?>) groovyContext.createShell().evaluate("{ ->\n" + groovySource + "\n}");
    }

    /**
     * Compiles a snippet into a single-parameter closure (e.g. {@code request}/{@code response}), for
     * hooks invoked with one argument.
     */
    public Closure<?> compile(String groovySource, String parameterName) {
        return (Closure<?>) groovyContext.createShell().evaluate("{ " + parameterName + " ->\n" + groovySource + "\n}");
    }

    /**
     * Compiles {@code groovySource} and evaluates it immediately with {@code delegate} bound
     * ({@code DELEGATE_FIRST}), returning the script's result. Used for build-time expressions such as
     * a {@code supportedFilter} spec ({@code attribute("id").eq().anySingleValue()}), where the delegate
     * is the builder and the result is the produced specification.
     */
    public Object evaluate(String groovySource, Object delegate) {
        Closure<?> closure = compile(groovySource);
        closure.setDelegate(delegate);
        closure.setResolveStrategy(Closure.DELEGATE_FIRST);
        return closure.call();
    }
}
