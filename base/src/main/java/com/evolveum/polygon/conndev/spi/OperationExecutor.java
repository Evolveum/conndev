/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 */
package com.evolveum.polygon.conndev.spi;

import com.evolveum.polygon.conndev.api.ContextLookup;
import org.identityconnectors.framework.common.exceptions.ConnectorException;

import java.util.Objects;

/**
 * Owns the execution boundary of a complete CRUD operation, not of each individual handler.
 * Independently invoked top-level operations are not automatically enlisted in an existing transaction;
 * internal follow-up work must use the supplied context instead of invoking another executor.
 */
public interface OperationExecutor {

    <T> T execute(Work<T> work);

    @FunctionalInterface
    interface Work<T> {
        T execute(ContextLookup context) throws Exception;
    }

    @FunctionalInterface
    interface TransactionFactory {
        /** Opens a fresh transaction per call; cleans up its own resources if opening fails. */
        OperationTransaction open() throws Exception;
    }

    /** Plain execution, e.g. REST. This deliberately provides no rollback guarantee. */
    static OperationExecutor direct(ContextLookup context) {
        Objects.requireNonNull(context, "context");
        return new OperationExecutor() {
            @Override
            public <T> T execute(Work<T> work) {
                try {
                    return work.execute(context);
                } catch (Exception e) {
                    throw failure(e);
                }
            }
        };
    }

    /**
     * Executes once, commits on success, and attempts rollback on work or commit failure.
     * Commit failures may have an uncertain outcome: this executor never retries the work.
     * Resource closure after a successful commit must not cause an attempted rollback.
     */
    static OperationExecutor transactional(TransactionFactory factory) {
        Objects.requireNonNull(factory, "factory");
        return new OperationExecutor() {
            @Override
            public <T> T execute(Work<T> work) {
                try (var transaction = Objects.requireNonNull(factory.open(), "transaction")) {
                    try {
                        var result = work.execute(transaction);
                        transaction.commit();
                        return result;
                    } catch (Exception | Error e) {
                        try {
                            transaction.rollback();
                        } catch (Exception | Error rollbackFailure) {
                            if (rollbackFailure != e) {
                                e.addSuppressed(rollbackFailure);
                            }
                        }
                        throw e;
                    }
                } catch (Exception e) {
                    throw failure(e);
                }
            }
        };
    }

    private static RuntimeException failure(Exception e) {
        if (e instanceof InterruptedException) {
            Thread.currentThread().interrupt();
        }
        return e instanceof RuntimeException runtime
                ? runtime : new ConnectorException("Operation execution failed", e);
    }
}
