/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.spi;

import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.identityconnectors.framework.common.objects.OperationOptions;
import org.identityconnectors.framework.common.objects.Uid;

import java.util.Collection;
import java.util.List;

/** Runs dependent-data cleanup before the primary deletion in one scope. */
public class DeleteOperationStrategyHandler implements ObjectDeleteOperation {

    private final OperationExecutor executor;
    private final DeleteOperationHandler primary;
    private final List<DeleteOperationHandler> cleanup;

    public DeleteOperationStrategyHandler(OperationExecutor executor,
            Collection<DeleteOperationHandler> primaryHandlers,
            Collection<DeleteOperationHandler> cleanup) {
        this.executor = executor;
        this.primary = primaryHandlers.stream().findFirst().orElseThrow(
                () -> new ConnectorException("No delete handler configured"));
        this.cleanup = List.copyOf(cleanup);
    }

    @Override
    public void delete(Uid uid, OperationOptions options) {
        executor.execute(scope -> {
            for (var handler : cleanup) {
                handler.delete(uid, options, scope);
            }
            primary.delete(uid, options, scope);
            return null;
        });
    }
}
