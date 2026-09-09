/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.groovy;

import com.evolveum.polygon.conndev.build.api.CreateOperationBuilder;
import com.evolveum.polygon.conndev.schema.BaseAttributeDefinition;
import com.evolveum.polygon.conndev.schema.BaseObjectClassDefinition;
import com.evolveum.polygon.conndev.spi.*;

import java.util.Collection;
import java.util.List;

public abstract class AbstractCreateOperationBuilder<OC extends BaseObjectClassDefinition<? extends BaseAttributeDefinition>>
        extends AbstractOperationBuilder<OC, CreateOperationBuilder> implements CreateOperationBuilder {

    protected AbstractCreateOperationBuilder() {
        super();
    }

    protected AbstractCreateOperationBuilder(BaseObjectOperationSupportBuilder<?,?,?,?,OC> parent) {
        super(parent);
    }

    @Override
    public ObjectCreateOperation build() {
        var handlers = collectHandlers();
        if (handlers.isEmpty()) {
            return null;
        }
        return new CreateOperationStrategyHandler(operationExecutor(), handlers, attributeHandlers());
    }

    protected OperationExecutor operationExecutor() {
        return OperationExecutor.direct(parent.context);
    }

    protected Collection<AttributeCreateOperationHandler> attributeHandlers() {
        return List.of();
    }

    protected Collection<CreateOperationHandler> collectHandlers() {
        throw new UnsupportedOperationException("collectHandlers() not implemented - override build() directly instead");
    }
}
