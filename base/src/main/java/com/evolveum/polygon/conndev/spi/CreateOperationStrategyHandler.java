/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.spi;

import com.evolveum.polygon.conndev.logging.ConnDevLog;
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

    private static final ConnDevLog LOG = ConnDevLog.of(CreateOperationStrategyHandler.class);

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

        var facade = LOG;
        var allHandlers = new ArrayList<Object>();
        allHandlers.add(primary.handler());
        for (var capability : selected) {
            allHandlers.add(capability.handler());
        }
        var labels = OperationTracing.labels(allHandlers);
        var attributesPerHandler = new ArrayList<List<String>>();
        attributesPerHandler.add(OperationTracing.attributeNames(primary.supported()));
        for (var capability : selected) {
            attributesPerHandler.add(OperationTracing.attributeNames(capability.supported()));
        }
        OperationTracing.detail(facade, OperationTracing.ROUTING,
                OperationTracing.routing(labels, attributesPerHandler));

        var selectedPrimary = primary;
        return executor.execute(scope -> {
            OperationTracing.executing(facade, labels.get(0),
                    OperationTracing.attributeNames(selectedPrimary.supported()));
            var result = selectedPrimary.handler().create(
                    Set.copyOf(selectedPrimary.supported()), options, scope);
            if (result == null || result.uid() == null || result.object() == null) {
                throw new ConnectorException("Primary create handler returned an incomplete result");
            }
            for (var i = 0; i < selected.size(); i++) {
                var capability = selected.get(i);
                OperationTracing.executing(facade, labels.get(i + 1),
                        OperationTracing.attributeNames(capability.supported()));
                capability.handler().create(new AttributeCreateOperationHandler.Request(
                        result.cls(), result.uid(), Set.copyOf(capability.supported())), options, scope);
            }
            return result.object();
        });
    }
}
