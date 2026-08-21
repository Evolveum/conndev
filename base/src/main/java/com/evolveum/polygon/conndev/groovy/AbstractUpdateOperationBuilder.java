/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.groovy;

import com.evolveum.polygon.conndev.build.api.UpdateOperationBuilder;
import com.evolveum.polygon.conndev.schema.BaseAttributeDefinition;
import com.evolveum.polygon.conndev.schema.BaseObjectClassDefinition;
import com.evolveum.polygon.conndev.spi.ObjectUpdateOperation;
import com.evolveum.polygon.conndev.spi.UpdateOperationHandler;
import com.evolveum.polygon.conndev.spi.UpdateOperationStrategyHandler;

import java.util.Collection;

public abstract class AbstractUpdateOperationBuilder<OC extends BaseObjectClassDefinition<? extends BaseAttributeDefinition>>
        extends AbstractOperationBuilder<OC, UpdateOperationBuilder> implements UpdateOperationBuilder {

    protected AbstractUpdateOperationBuilder() {
        super();
    }

    protected AbstractUpdateOperationBuilder(BaseObjectOperationSupportBuilder<?,?,?,?,OC> parent) {
        super(parent);
    }

    @Override
    public ObjectUpdateOperation build() {
        if (isEmpty()) {
            return null;
        }
        var handlers = collectHandlers();
        return new UpdateOperationStrategyHandler(parent.context, parent.getObjectClass().objectClass(), handlers);
    }

    /**
     * Cheap pre-check run before {@link #collectHandlers()}. Subclasses that use the
     * {@link #build()} template override this instead of {@link #build()} itself.
     */
    protected boolean isEmpty() {
        return collectHandlers().isEmpty();
    }

    protected Collection<UpdateOperationHandler> collectHandlers() {
        throw new UnsupportedOperationException("collectHandlers() not implemented - override build() directly instead");
    }
}
