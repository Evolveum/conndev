/*
 * Copyright (c) 2026 Evolveum and contributors
 * 
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 * 
 */
package com.evolveum.polygon.conndev.build.api;

import com.evolveum.polygon.conndev.spi.ObjectCreateOperation;

/**
 * Builder for the create operation.
 *
 * <p>Currently a marker interface with no additional methods beyond {@code build()}.
 * Extend if create-specific configuration is needed in the future.</p>
 *
 * @see ObjectClassOperationBuilder
 */
public interface CreateOperationBuilder extends ObjectClassOperationBuilder<CreateOperationBuilder, ObjectCreateOperation> {
}
