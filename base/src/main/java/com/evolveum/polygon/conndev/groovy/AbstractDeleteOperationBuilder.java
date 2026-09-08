/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.groovy;

import com.evolveum.polygon.conndev.build.api.DeleteOperationBuilder;
import com.evolveum.polygon.conndev.schema.BaseAttributeDefinition;
import com.evolveum.polygon.conndev.schema.BaseObjectClassDefinition;
import com.evolveum.polygon.conndev.spi.DeleteOperationHandler;
import com.evolveum.polygon.conndev.spi.DeleteOperationStrategyHandler;
import com.evolveum.polygon.conndev.spi.ObjectDeleteOperation;
import com.evolveum.polygon.conndev.spi.OperationExecutor;

import java.util.Collection;
import java.util.List;

public abstract class AbstractDeleteOperationBuilder<OC extends BaseObjectClassDefinition<? extends BaseAttributeDefinition>>
        extends AbstractOperationBuilder<OC, DeleteOperationBuilder> implements DeleteOperationBuilder {

    protected AbstractDeleteOperationBuilder() {
        super();
    }

    protected AbstractDeleteOperationBuilder(BaseObjectOperationSupportBuilder<?,?,?,?,OC> parent) {
        super(parent);
    }

    @Override
    public ObjectDeleteOperation build() {
        var handlers = collectHandlers();
        if (handlers.isEmpty()) {
            return null;
        }
        return new DeleteOperationStrategyHandler(operationExecutor(), handlers, cleanupHandlers());
    }

    protected OperationExecutor operationExecutor() {
        return OperationExecutor.direct(parent.context);
    }

    protected Collection<DeleteOperationHandler> cleanupHandlers() {
        return List.of();
    }

    protected Collection<DeleteOperationHandler> collectHandlers() {
        throw new UnsupportedOperationException("collectHandlers() not implemented - override build() directly instead");
    }
}
