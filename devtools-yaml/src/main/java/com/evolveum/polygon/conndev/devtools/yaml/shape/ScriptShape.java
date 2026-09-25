/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.devtools.yaml.shape;

/**
 * A Groovy script value: the YAML block scalar (or, for {@code expression} leaves, a single
 * build-time expression) compiled to a {@code Closure} by the runtime binder. An explicit
 * {@code null} is accepted by the binder and leaves the default untouched.
 */
public final class ScriptShape implements YamlShape {

    private final boolean expression;
    private final boolean emptyBody;
    private final String source;
    private final String description;
    private final boolean deprecated;

    public ScriptShape(boolean expression, boolean emptyBody, String source, String description, boolean deprecated) {
        this.expression = expression;
        this.emptyBody = emptyBody;
        this.source = source;
        this.description = description;
        this.deprecated = deprecated;
    }

    /**
     * {@code true} for a single-expression leaf (a search filter {@code spec}); {@code false} for a
     * closure body (an {@code implementation} block).
     */
    public boolean expression() {
        return expression;
    }

    /** Whether an empty (blank) body is a legitimate value. */
    public boolean emptyBody() {
        return emptyBody;
    }

    @Override
    public String source() {
        return source;
    }

    @Override
    public String description() {
        return description;
    }

    @Override
    public boolean deprecated() {
        return deprecated;
    }

    @Override
    public String toString() {
        return expression ? "script(expression)" : "script(block" + (emptyBody ? ",empty" : "") + ")";
    }
}
