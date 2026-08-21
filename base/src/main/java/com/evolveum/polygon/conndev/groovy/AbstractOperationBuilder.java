/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.groovy;

import com.evolveum.polygon.conndev.concepts.DefinitionValue;
import com.evolveum.polygon.conndev.schema.BaseAttributeDefinition;
import com.evolveum.polygon.conndev.schema.BaseObjectClassDefinition;

/**
 * Shared {@code enabled} flag and {@code parent} link for the per-operation builders
 * (create/update/delete/search).
 *
 * @param <OC> the connector's object class definition type
 * @param <B> the concrete operation-builder interface the subclass fixes itself to
 *            (e.g. {@code CreateOperationBuilder}), letting {@link #enabled(DefinitionValue)}
 *            return that type without every subclass re-declaring the same override
 */
public abstract class AbstractOperationBuilder<OC extends BaseObjectClassDefinition<? extends BaseAttributeDefinition>, B> {

    protected final BaseObjectOperationSupportBuilder<?,?,?,?,OC> parent;
    protected DefinitionValue<Boolean> enabled = DefinitionValue.DEFAULT_TRUE;

    /**
     * Only for subclasses that override {@code build()} entirely and never use the
     * {@code parent}-dependent template (currently connector-sql). Remove once that
     * connector is migrated to the shared {@code build()}/{@code collectHandlers()} template.
     */
    protected AbstractOperationBuilder() {
        this.parent = null;
    }

    protected AbstractOperationBuilder(BaseObjectOperationSupportBuilder<?,?,?,?,OC> parent) {
        this.parent = parent;
    }

    public boolean isEnabled() {
        return enabled.value();
    }

    @SuppressWarnings("unchecked")
    public B enabled(DefinitionValue<Boolean> value) {
        enabled = enabled.moreSpecific(value);
        return (B) this;
    }
}
