/*
 * Copyright (c) 2026 Evolveum and contributors
 * 
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 * 
 */
package com.evolveum.polygon.conndev.build.api;

import com.evolveum.polygon.conndev.spi.ObjectDeleteOperation;

/**
 * Builder for the delete operation.
 *
 * <p>Currently a marker interface with no additional methods beyond {@code build()}.
 * Extend if delete-specific configuration is needed in the future.</p>
 *
 * @see ObjectClassOperationBuilder
 */
public interface DeleteOperationBuilder extends ObjectClassOperationBuilder<ObjectDeleteOperation> {
}
