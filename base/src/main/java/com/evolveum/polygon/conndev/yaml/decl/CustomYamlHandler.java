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
}
