/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.devtools.yaml.shape;

import java.util.List;

/**
 * An object shape whose property set is not fully declarable in the shape file: the explicitly
 * declared keys (the ones a {@code @Yaml.Custom} handler consumes, e.g. an endpoint's
 * {@code method}/{@code path}) plus the {@code @Yaml.*} bindings of one or more delegate types
 * (the builder the handler binds the remaining keys onto).
 *
 * <p>This is an intermediate kind only: {@code SchemaGeneration} resolves every delegate type
 * (scanning it through the {@link com.evolveum.polygon.conndev.devtools.yaml.scan.BindingScanner})
 * and merges the result with the declared keys, replacing this node with a plain
 * {@link ObjectShape}. Generators never see an unresolved instance.
 */
public final class DelegatedObjectShape implements YamlShape {

    private final ObjectShape declared;
    private final List<String> delegateTypes;
    private final String description;
    private final boolean deprecated;

    public DelegatedObjectShape(ObjectShape declared, List<String> delegateTypes, String description, boolean deprecated) {
        this.declared = declared;
        this.delegateTypes = List.copyOf(delegateTypes);
        this.description = description;
        this.deprecated = deprecated;
    }

    /** The explicitly declared keys (may be empty). */
    public ObjectShape declared() {
        return declared;
    }

    /** The fully-qualified names of the builder types whose {@code @Yaml.*} bindings are merged in. */
    public List<String> delegateTypes() {
        return delegateTypes;
    }

    @Override
    public String source() {
        return "delegate:" + String.join(" + ", delegateTypes);
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
        return "delegated-object(" + String.join(" + ", delegateTypes) + ")";
    }
}
