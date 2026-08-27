/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.concepts;

/**
 * A detected effect produced by a {@link MappingRule}, deferred until the relevant builder is
 * actually reachable — schema effects are applied during schema {@code build()}, handler effects
 * later, once handlers exist. An implementation overrides only the method(s) it needs; the rest
 * default to no-op, so a single class can carry a schema-only, a handler-only, or a dual effect
 * without implementing unrelated methods.
 *
 * @param <OC> the object-class builder type
 * @param <A>  the attribute builder type
 * @param <H>  the handler builder type
 */
public interface MappingAction<OC, A, H> {

    /**
     * Apply this action's effect to the object class builder.
     *
     * @param objectClass the object class builder
     */
    default void applyToSchema(OC objectClass) {
    }

    /**
     * Apply this action's effect to a specific attribute builder.
     *
     * @param attribute the attribute builder
     */
    default void applyToAttribute(A attribute) {
    }

    /**
     * Apply this action's effect to the operation handler builder.
     *
     * @param handlerBuilder the operation support builder for this object class
     */
    default void applyToHandler(H handlerBuilder) {
    }
}
