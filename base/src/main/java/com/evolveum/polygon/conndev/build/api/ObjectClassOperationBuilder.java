/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.build.api;

/**
 * Base interface for all ConnId operation builders.
 *
 * <p>This is the terminal builder interface extended by all operation-specific builders
 * (create, read, update, delete, search, list). Implementations must provide a
 * {@code build()} method that returns the typed operation instance.</p>
 *
 * @param <T> The operation type produced by {@code build()}
 */
public interface ObjectClassOperationBuilder<T> {

    /**
     * Builds the operation instance.
     *
     * @return the built operation
     */
    T build();
}
