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
import com.evolveum.polygon.conndev.spi.CreateOperationHandler;
import com.evolveum.polygon.conndev.spi.CreateOperationStrategyHandler;
import com.evolveum.polygon.conndev.spi.ObjectCreateOperation;

import java.util.Collection;

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
        return new CreateOperationStrategyHandler(parent.context, parent.getObjectClass().objectClass(), handlers);
    }

    /**
     * Collects the handlers to dispatch through. Subclasses that use the {@link #build()}
     * template override this instead of {@link #build()} itself.
     */
    protected Collection<CreateOperationHandler> collectHandlers() {
        throw new UnsupportedOperationException("collectHandlers() not implemented - override build() directly instead");
    }
}
