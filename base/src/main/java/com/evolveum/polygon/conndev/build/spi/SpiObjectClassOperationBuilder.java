/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.build.spi;

import com.evolveum.polygon.conndev.concepts.FluentBuilder;

/**
 * SPI-level object class operation builder base interface.
 *
 * <p>This interface is the service-provider counterpart to
 * {@link com.evolveum.polygon.conndev.build.api.ObjectClassOperationBuilder}.
 * SPI implementations use DefinitionValue to carry provenance metadata
 * for operation definitions.</p>
 */
public interface SpiObjectClassOperationBuilder extends FluentBuilder<SpiObjectClassOperationBuilder, SpiObjectClassOperationBuilder.SpiOperationDefinition> {

    /**
     * An operation definition descriptor.
     */
    class SpiOperationDefinition {
    }
}