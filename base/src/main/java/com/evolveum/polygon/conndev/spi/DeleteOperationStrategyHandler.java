/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.spi;

import com.evolveum.polygon.conndev.logging.ConnDevLog;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.identityconnectors.framework.common.objects.OperationOptions;
import org.identityconnectors.framework.common.objects.Uid;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** Runs dependent-data cleanup before the primary deletion in one scope. */
public class DeleteOperationStrategyHandler implements ObjectDeleteOperation {

    private static final ConnDevLog LOG = ConnDevLog.of(DeleteOperationStrategyHandler.class);

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
        var facade = LOG;
        var all = new ArrayList<DeleteOperationHandler>();
        all.addAll(cleanup);
        all.add(primary);
        var labels = OperationTracing.labels(all);
        var cleanupLabels = labels.subList(0, cleanup.size());
        var primaryLabel = labels.getLast();
        if (!cleanupLabels.isEmpty()) {
            OperationTracing.detail(facade, OperationTracing.CLEANUP, cleanupLabels);
        }
        executor.execute(scope -> {
            for (var i = 0; i < cleanup.size(); i++) {
                OperationTracing.executing(facade, cleanupLabels.get(i), List.of());
                cleanup.get(i).delete(uid, options, scope);
            }
            OperationTracing.executing(facade, primaryLabel, List.of());
            primary.delete(uid, options, scope);
            return null;
        });
    }
}
