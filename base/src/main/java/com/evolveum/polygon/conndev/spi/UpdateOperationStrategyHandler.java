/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.spi;

import com.evolveum.polygon.conndev.api.ContextLookup;
import com.evolveum.polygon.conndev.build.api.UpdateOperationBuilder;
import com.evolveum.polygon.conndev.groovy.ConnectorContext;
import com.evolveum.polygon.conndev.logging.ConnDevLog;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.identityconnectors.framework.common.objects.AttributeDelta;
import org.identityconnectors.framework.common.objects.ConnectorObject;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.OperationOptions;
import org.identityconnectors.framework.common.objects.Uid;
import org.identityconnectors.framework.common.objects.filter.EqualsFilter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/** Routes deltas in registration order and executes the selected handlers in one scope. */
public class UpdateOperationStrategyHandler implements ObjectUpdateOperation {

    private static final ConnDevLog LOG = ConnDevLog.of(UpdateOperationStrategyHandler.class);

    private final ConnectorContext context;
    private final ObjectClass objectClass;
    private final OperationExecutor executor;
    private final List<UpdateOperationHandler> handlers;

    public UpdateOperationStrategyHandler(ConnectorContext context, ObjectClass objectClass,
            OperationExecutor executor, Collection<UpdateOperationHandler> handlers) {
        this.context = context;
        this.objectClass = objectClass;
        this.executor = executor;
        this.handlers = List.copyOf(handlers);
    }

    @Override
    public Set<AttributeDelta> updateDelta(
            Uid uid, Set<AttributeDelta> modifications, OperationOptions options) {
        var requested = modifications != null ? Set.copyOf(modifications) : Set.<AttributeDelta>of();
        if (requested.isEmpty()) {
            return requested;
        }
        var routing = new AttributeOperationRouting<>(requested);
        var selected = new ArrayList<
                AttributeAwareOperationHandler.Capability<AttributeDelta, UpdateOperationHandler>>();
        var originalRequired = false;
        for (var handler : handlers) {
            var capability = routing.select(handler, options);
            if (!capability.isUnsupported()) {
                selected.add(capability);
                originalRequired |= handler.requiresOriginalState();
            }
        }
        routing.requireComplete();
        var readOriginal = originalRequired;
        var facade = LOG;
        var selectedHandlers = new ArrayList<UpdateOperationHandler>(selected.size());
        for (var capability : selected) {
            selectedHandlers.add(capability.handler());
        }
        var labels = OperationTracing.labels(selectedHandlers);
        var attributesPerHandler = new ArrayList<List<String>>(selected.size());
        for (var capability : selected) {
            attributesPerHandler.add(OperationTracing.deltaNames(capability.supported()));
        }
        OperationTracing.detail(facade, OperationTracing.ROUTING,
                OperationTracing.routing(labels, attributesPerHandler));
        OperationTracing.detail(facade, OperationTracing.READ_ORIGINAL, readOriginal);

        return executor.execute(scope -> {
            var before = readOriginal ? readObject(uid, options, scope) : null;
            if (before != null) {
                OperationTracing.detail(facade, OperationTracing.ORIGINAL_STATE, uid.getUidValue());
            }
            for (var i = 0; i < selected.size(); i++) {
                var capability = selected.get(i);
                OperationTracing.executing(facade, labels.get(i),
                        OperationTracing.deltaNames(capability.supported()));
                capability.handler().update(new UpdateOperationBuilder.UpdateRequest(
                        objectClass, uid, capability.supported(), before), options, scope);
            }
            return requested;
        });
    }

    private ConnectorObject readObject(Uid uid, OperationOptions options,
            ContextLookup operationContext) {
        var result = new ArrayList<ConnectorObject>();
        context.handlerFor(objectClass).checkSupported(ObjectSearchOperation.class)
                .executeQuery(operationContext, new EqualsFilter(uid), result::add, options);
        return result.stream().findFirst().orElseThrow(
                () -> new ConnectorException("Unable to read previous state for " + uid));
    }
}
