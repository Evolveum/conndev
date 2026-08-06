/*
 * Copyright (c) 2025-2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.build.api;

import com.evolveum.polygon.conndev.build.spi.SpiObjectClassHandlerBuilder;
import com.evolveum.polygon.conndev.concepts.DefinitionValue;
import com.evolveum.polygon.conndev.concepts.SourceLocation;
import com.evolveum.polygon.conndev.spi.ObjectClassOperation;

/**
 * Base interface for all ConnId operation builders.
 *
 * <p>This is the terminal builder interface extended by all operation-specific builders
 * (create, read, update, delete, search, list). Implementations must provide a
 * {@code build()} method that returns the typed operation instance.</p>
 *
 * @param <T> The operation type produced by {@code build()}
 */
public interface ObjectClassOperationBuilder<B extends ObjectClassOperationBuilder<B,P>, P extends ObjectClassOperation> extends SpiObjectClassHandlerBuilder<B, P> {

    /**
     * Configures whether this operation is enabled.
     *
     * @param enabled the flag indicating if the operation should be enabled
     * @return the builder instance for method chaining
     */
    default B enabled(boolean enabled) {
        enabled(DefinitionValue.from(enabled, SourceLocation.capture()));
        return self();
    }


}
