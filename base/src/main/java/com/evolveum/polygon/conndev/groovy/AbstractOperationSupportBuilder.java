/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.groovy;

import com.evolveum.polygon.conndev.build.api.ObjectOperationSupportBuilder;
import com.evolveum.polygon.conndev.build.api.OperationSupportBuilder;
import com.evolveum.polygon.conndev.schema.BaseObjectClassDefinition;
import com.evolveum.polygon.conndev.spi.CompositeObjectClassHandler;
import org.identityconnectors.framework.common.objects.ObjectClass;

import java.util.HashMap;
import java.util.Map;

public abstract class AbstractOperationSupportBuilder<
        PB extends OperationSupportBuilder<PB,PO>,
        PO extends ObjectOperationSupportBuilder> implements OperationSupportBuilder<PB,PO> {

    private final ConnectorContext context;
    // Keyed by ObjectClass rather than raw String: ConnId's ObjectClass identity is
    // case-insensitive (see ObjectClass.is()/equals()), so "User" and "user" must resolve to the
    // same handler builder instead of silently producing two competing ones.
    private final Map<ObjectClass, PO> handlers = new HashMap<>();

    protected AbstractOperationSupportBuilder(ConnectorContext context) {
        this.context = context;
    }

    @Override
    public PO objectClass(String user) {
        return handlers.computeIfAbsent(new ObjectClass(user), k -> newObjectSpecific(context.schema().objectClass(user)));
    }

    protected abstract PO newObjectSpecific(BaseObjectClassDefinition classDefinition);

    public Map<ObjectClass, CompositeObjectClassHandler> build() {
        Map<ObjectClass, CompositeObjectClassHandler> ret = new HashMap<>();
        for (var builder : handlers.values()) {
            var handler = builder.build();
            if (handler != null) {
                ret.put(handler.objectClass(), handler);
            }
        }
        return ret;
    }
}
