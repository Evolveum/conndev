/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.groovy;

import com.evolveum.polygon.conndev.build.api.OperationBuilder;
import com.evolveum.polygon.conndev.schema.BaseObjectClassDefinition;
import com.evolveum.polygon.conndev.spi.ObjectClassHandler;
import org.identityconnectors.framework.common.objects.ObjectClass;

import java.util.HashMap;
import java.util.Map;

public abstract class AbstractOperationBuilder<O extends BaseObjectOperationSupportBuilder<?,?,?,?>> implements OperationBuilder {

    private final ConnectorContext context;
    private final Map<String, O> handlers = new HashMap<>();

    protected AbstractOperationBuilder(ConnectorContext context) {
        this.context = context;
    }

    @Override
    public O objectClass(String user) {
        return handlers.computeIfAbsent(user, k ->  newObjectSpecific(context,context.schema().objectClass(user)));
    }

    protected abstract O newObjectSpecific(ConnectorContext context, BaseObjectClassDefinition classDefinition);

    public Map<ObjectClass, ObjectClassHandler> build() {
        Map<ObjectClass, ObjectClassHandler> ret = new HashMap<>();
        for (var builder : handlers.values()) {
            var handler = builder.build();
            if (handler != null) {
                ret.put(handler.objectClass(), handler);
            }
        }
        return ret;
    }
}
