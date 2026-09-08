/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.spi;

import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.identityconnectors.framework.common.objects.OperationOptions;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** Assigns each request item to the first registered handler that supports it. */
final class AttributeOperationRouting<R> {

    private final List<R> outstanding;

    AttributeOperationRouting(Collection<R> request) {
        outstanding = new ArrayList<>(request);
    }

    <H extends AttributeAwareOperationHandler<R, H>> AttributeAwareOperationHandler.Capability<R, H> inspect(
            H handler, OperationOptions options) {
        var capability = handler.canHandle(List.copyOf(outstanding), options);
        if (capability == null) {
            throw new ConnectorException("Handler returned no capability");
        }
        var supported = capability.supported() == null ? List.<R>of() : List.copyOf(capability.supported());
        if (!outstanding.containsAll(supported)) {
            throw new ConnectorException("Handler claimed items outside its request");
        }
        return new AttributeAwareOperationHandler.Capability<>(handler, supported);
    }

    <H extends AttributeAwareOperationHandler<R, H>> AttributeAwareOperationHandler.Capability<R, H> select(
            H handler, OperationOptions options) {
        var capability = inspect(handler, options);
        claim(capability.supported());
        return capability;
    }

    void claim(Collection<R> supported) {
        outstanding.removeAll(supported);
    }

    void requireComplete() {
        if (!outstanding.isEmpty()) {
            throw new ConnectorException("Unsupported attributes: " + outstanding);
        }
    }
}
