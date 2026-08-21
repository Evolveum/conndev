/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.groovy;

import com.evolveum.polygon.conndev.build.api.DeleteOperationBuilder;
import com.evolveum.polygon.conndev.concepts.DefinitionValue;
import com.evolveum.polygon.conndev.schema.BaseAttributeDefinition;
import com.evolveum.polygon.conndev.schema.BaseObjectClassDefinition;
import com.evolveum.polygon.conndev.spi.DeleteOperationHandler;
import com.evolveum.polygon.conndev.spi.DeleteOperationStrategyHandler;
import com.evolveum.polygon.conndev.spi.ObjectDeleteOperation;

import java.util.Collection;

public abstract class AbstractDeleteOperationBuilder<OC extends BaseObjectClassDefinition<? extends BaseAttributeDefinition>> implements DeleteOperationBuilder {

    protected final BaseObjectOperationSupportBuilder<?,?,?,?,OC> parent;
    protected DefinitionValue<Boolean> enabled = DefinitionValue.DEFAULT_TRUE;

    /**
     * Only for subclasses that override {@link #build()} entirely and never use the
     * {@code parent}-dependent template (currently connector-sql). Remove once that
     * connector is migrated to the shared {@link #build()}/{@link #collectHandlers()} template.
     */
    protected AbstractDeleteOperationBuilder() {
        this.parent = null;
    }

    protected AbstractDeleteOperationBuilder(BaseObjectOperationSupportBuilder<?,?,?,?,OC> parent) {
        this.parent = parent;
    }

    @Override
    public boolean isEnabled() {
        return enabled.value();
    }

    @Override
    public DeleteOperationBuilder enabled(DefinitionValue<Boolean> value) {
        enabled = enabled.moreSpecific(value);
        return this;
    }

    @Override
    public ObjectDeleteOperation build() {
        var handlers = collectHandlers();
        if (handlers.isEmpty()) {
            return null;
        }
        return new DeleteOperationStrategyHandler(handlers);
    }

    /**
     * Collects the handlers to dispatch through. Subclasses that use the {@link #build()}
     * template override this instead of {@link #build()} itself.
     */
    protected Collection<DeleteOperationHandler> collectHandlers() {
        throw new UnsupportedOperationException("collectHandlers() not implemented - override build() directly instead");
    }
}
