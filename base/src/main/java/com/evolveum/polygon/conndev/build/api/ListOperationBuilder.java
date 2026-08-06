/*
 * Copyright (c) 2026 Evolveum and contributors
 * 
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 * 
 */
package com.evolveum.polygon.conndev.build.api;

import com.evolveum.polygon.conndev.spi.ObjectClassOperation;

/**
 * Builder for the list operation.
 *
 * <p>Currently a marker interface with no additional methods. Extend if list-specific
 * configuration (pagination, filtering mode) is needed in the future.</p>
 *
 * @see ObjectClassOperationBuilder
 */
public interface ListOperationBuilder extends ObjectClassOperationBuilder<ListOperationBuilder, ObjectClassOperation> {
}
