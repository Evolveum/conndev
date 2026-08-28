/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.api;

/**
 * A no-op {@link AttributePathFormat} whose source is the {@link AttributePath} itself:
 * both {@link #parse} and {@link #serialize} pass the path through unchanged.
 *
 * <p>Used to store programmatically built {@link AttributePath} instances in an
 * {@link AttributePathDeclaration} together with their source location.</p>
 */
public final class JavaPathFormat implements AttributePathFormat<AttributePath> {

    public static final JavaPathFormat INSTANCE = new JavaPathFormat();

    private JavaPathFormat() {
        // intentionally empty
    }

    @Override
    public AttributePath parse(AttributePath source) {
        return source;
    }

    @Override
    public AttributePath serialize(AttributePath path) {
        return path;
    }
}
