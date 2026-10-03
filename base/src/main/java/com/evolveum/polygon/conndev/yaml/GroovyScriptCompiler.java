/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.yaml;

import com.evolveum.polygon.conndev.groovy.GroovyContext;
import com.evolveum.polygon.conndev.groovy.GroovyExceptionSanitizer;
import groovy.lang.Closure;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.regex.Pattern;

/**
 * Compiles YAML block scalars carrying Groovy source (e.g. {@code implementation: |}) into
 * {@link Closure}s, so imperative logic keeps working under the declarative YAML envelope.
 *
 * <p>The snippet is wrapped in a closure literal and evaluated on the shared {@link GroovyContext}
 * shell. The delegate and resolve strategy are set later by the builders, exactly as for a closure
 * written in the Groovy DSL — so the runtime context and its members resolve identically.
 *
 * <p>A fragment may carry its own {@code import} statements — written as regular lines at the
 * top of the block. They are hoisted out of the wrapping closure (imports are only legal at the
 * top of the compiled script, not inside a closure body):
 * <pre>
 * import java.math.BigInteger
 * return new BigInteger("42")
 * </pre>
 */
public final class GroovyScriptCompiler {

    /** A leading import statement: {@code import a.b.C} / {@code import a.b.*}, an optional trailing semicolon. */
    private static final Pattern IMPORT_LINE = Pattern.compile("\\s*import\\s+[\\w.]+(\\.\\*)?;?\\s*");

    private final GroovyContext groovyContext;

    public GroovyScriptCompiler(GroovyContext groovyContext) {
        this.groovyContext = groovyContext;
    }

    public Closure<?> compile(String groovySource) {
        return (Closure<?>) groovyContext.createShell().evaluate(wrap(groovySource, null));
    }

    /**
     * Compiles a snippet into a single-parameter closure (e.g. {@code request}/{@code response}), for
     * hooks invoked with one argument.
     */
    public Closure<?> compile(String groovySource, String parameterName) {
        return (Closure<?>) groovyContext.createShell().evaluate(wrap(groovySource, parameterName));
    }

    /**
     * Wraps the fragment as a closure literal, hoisting any leading import statements out of the
     * closure body (they are only legal at the top of the compiled script, not inside a closure).
     * A fragment without imports wraps exactly as before.
     */
    static String wrap(String groovySource, String parameterName) {
        var lines = groovySource.split("\n", -1);
        var imports = new ArrayList<String>();
        int firstBody = 0;
        while (firstBody < lines.length && IMPORT_LINE.matcher(lines[firstBody]).matches()) {
            imports.add(lines[firstBody].trim());
            firstBody++;
        }
        var body = String.join("\n", Arrays.copyOfRange(lines, firstBody, lines.length));
        var closure = (parameterName == null ? "{ ->\n" : "{ " + parameterName + " ->\n") + body + "\n}";
        return imports.isEmpty() ? closure : String.join("\n", imports) + "\n" + closure;
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
        try {
            return closure.call();
        } catch (RuntimeException e) {
            throw GroovyExceptionSanitizer.sanitize(e);
        }
    }
}
