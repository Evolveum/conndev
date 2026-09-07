/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.spi;

import com.evolveum.polygon.conndev.build.api.UpdateOperationBuilder;
import com.evolveum.polygon.conndev.groovy.ConnectorContext;
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

        return executor.execute(scope -> {
            var before = readOriginal ? readObject(uid, options, scope) : null;
            for (var capability : selected) {
                capability.handler().update(new UpdateOperationBuilder.UpdateRequest(
                        objectClass, uid, capability.supported(), before), options, scope);
            }
            return requested;
        });
    }

    private ConnectorObject readObject(Uid uid, OperationOptions options,
            com.evolveum.polygon.conndev.api.ContextLookup operationContext) {
        var result = new ArrayList<ConnectorObject>();
        context.handlerFor(objectClass).checkSupported(ObjectSearchOperation.class)
                .executeQuery(operationContext, new EqualsFilter(uid), result::add, options);
        return result.stream().findFirst().orElseThrow(
                () -> new ConnectorException("Unable to read previous state for " + uid));
    }
}
