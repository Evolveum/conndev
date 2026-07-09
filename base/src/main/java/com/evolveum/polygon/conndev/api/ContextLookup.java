/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.api;

import com.evolveum.polygon.conndev.concepts.RetrievableContext;

public interface ContextLookup {

    <T extends RetrievableContext> T get(Class<T> contextType) throws IllegalStateException;

    /**
     * Lookup with no contexts, for builders that must work without any runtime context (declarative
     * definitions, tests): every lookup fails fast.
     */
    static ContextLookup none() {
        return new ContextLookup() {
            @Override
            public <T extends RetrievableContext> T get(Class<T> contextType) {
                throw new IllegalStateException("No runtime context available (requested " + contextType + ")");
            }
        };
    }
}
