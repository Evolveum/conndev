/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.spi;

import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.identityconnectors.framework.common.objects.Attribute;
import org.identityconnectors.framework.common.objects.ConnectorObject;
import org.identityconnectors.framework.common.objects.OperationOptions;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/** Creates one primary object and then its separately stored attributes in one scope. */
public class CreateOperationStrategyHandler implements ObjectCreateOperation {

    private final OperationExecutor executor;
    private final List<CreateOperationHandler> primaryHandlers;
    private final List<AttributeCreateOperationHandler> attributeHandlers;

    public CreateOperationStrategyHandler(OperationExecutor executor,
            Collection<CreateOperationHandler> primaryHandlers,
            Collection<AttributeCreateOperationHandler> attributeHandlers) {
        this.executor = executor;
        this.primaryHandlers = List.copyOf(primaryHandlers);
        this.attributeHandlers = List.copyOf(attributeHandlers);
    }

    @Override
    public ConnectorObject create(Set<Attribute> createAttributes, OperationOptions options) {
        var requested = createAttributes != null ? Set.copyOf(createAttributes) : Set.<Attribute>of();
        var routing = new AttributeOperationRouting<>(requested);
        AttributeAwareOperationHandler.Capability<Attribute, CreateOperationHandler> primary = null;
        for (var handler : primaryHandlers) {
            var capability = routing.inspect(handler, options);
            if (primary == null) {
                primary = capability;
            }
            if (!capability.isUnsupported()) {
                primary = capability;
                break;
            }
        }
        if (primary == null) {
            throw new ConnectorException("No create handler configured");
        }
        routing.claim(primary.supported());

        var selected = new ArrayList<
                AttributeAwareOperationHandler.Capability<Attribute, AttributeCreateOperationHandler>>();
        for (var handler : attributeHandlers) {
            var capability = routing.select(handler, options);
            if (!capability.isUnsupported()) {
                selected.add(capability);
            }
        }
        routing.requireComplete();

        var selectedPrimary = primary;
        return executor.execute(scope -> {
            var result = selectedPrimary.handler().create(
                    Set.copyOf(selectedPrimary.supported()), options, scope);
            if (result == null || result.uid() == null || result.object() == null) {
                throw new ConnectorException("Primary create handler returned an incomplete result");
            }
            for (var capability : selected) {
                capability.handler().create(new AttributeCreateOperationHandler.Request(
                        result.cls(), result.uid(), Set.copyOf(capability.supported())), options, scope);
            }
            return result.object();
        });
    }
}
