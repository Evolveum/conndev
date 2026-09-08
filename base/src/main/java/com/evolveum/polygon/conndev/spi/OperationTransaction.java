/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 */
package com.evolveum.polygon.conndev.spi;

import com.evolveum.polygon.conndev.api.ContextLookup;

/**
 * Connector-provided transaction, already begun when returned to the executor.
 * Only the executor calls lifecycle methods; handlers use it only as a context lookup.
 */
public interface OperationTransaction extends ContextLookup, AutoCloseable {

    void commit() throws Exception;

    void rollback() throws Exception;

    @Override
    void close() throws Exception;
}
