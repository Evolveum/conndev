/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.build.spi;

import com.evolveum.polygon.conndev.concepts.DefinitionValue;
import com.evolveum.polygon.conndev.concepts.Fluent;

/**
 * SPI-level operational flag builder base interface.
 *
 * <p>This interface is the service-provider counterpart to the various API-level
 * builders that carry ConnId operational flags (readable, required, creatable,
 * updatable, etc.). SPI implementations use DefinitionValue to carry provenance
 * metadata for these operational definitions.</p>
 *
 * @param <B> The concrete builder type (self-type for CRTP)
 */
public interface SpiOperationBuilder<B extends SpiOperationBuilder<B>> extends Fluent<B> {

    /**
     * Marks the operation as supported.
     *
     * @param supported the supported flag with metadata
     * @return this builder for chaining
     */
    B supported(DefinitionValue<Boolean> supported);

    /**
     * Builds the operation instance.
     *
     * @return the built operation
     */
    Object build();
}