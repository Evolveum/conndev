/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.yaml.decl;

/**
 * Binds a non-generic YAML shape — a named map of sub-builders, a list of sub-builders, or a
 * protocol block ({@code sql:}, {@code scim:}) — onto a builder. The generic {@link DeclYamlBinder}
 * delegates these structural keys to a named handler (declared with {@code @Yaml.Custom}); each
 * handler is stateless and is shared across documents of the same type.
 */
public interface CustomYamlHandler {

    /**
     * @param binder the active binder (gives access to sub-binding, coercion and locations)
     * @param target the builder the structural key belongs to
     * @param value  the YAML value of the structural key
     */
    void apply(DeclYamlBinder binder, Object target, LocatedNode value);

    /**
     * Opts this handler's Groovy-bearing fields into the compile-phase syntax check — the generic
     * engine can't see inside an opaque {@code Custom} block. Call {@link
     * GroovySyntaxChecker#checkFragment} per field, or {@link GroovySyntaxChecker#checkFragments}
     * to delegate remaining entries to another type's {@code @Yaml.*} shape. Default no-op just
     * means this handler's fragments get checked later, at build time, not compile.
     *
     * @param value   the structural key's YAML value (same as {@link #apply}'s)
     * @param path    this key's dotted path, used as the resulting error's source
     * @param checker never call anything on it that binds or executes
     */
    default void checkGroovySyntax(LocatedNode value, String path, GroovySyntaxChecker checker) {
    }
}
