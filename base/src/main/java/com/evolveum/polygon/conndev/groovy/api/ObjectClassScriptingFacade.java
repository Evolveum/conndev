/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.groovy.api;

import com.evolveum.polygon.conndev.api.ContextLookup;
import com.evolveum.polygon.conndev.groovy.ConnectorContext;
import com.evolveum.polygon.conndev.schema.BaseAttributeDefinition;
import com.evolveum.polygon.conndev.schema.BaseObjectClassDefinition;
import com.evolveum.polygon.conndev.spi.ObjectClassHandler;
import com.evolveum.polygon.conndev.spi.ObjectSearchOperation;
import org.identityconnectors.framework.common.objects.OperationOptions;
import org.identityconnectors.framework.common.objects.ResultsHandler;
import org.identityconnectors.framework.common.objects.filter.Filter;


public record ObjectClassScriptingFacade(ContextLookup rest, BaseObjectClassDefinition<BaseAttributeDefinition> schema, ObjectClassHandler handler) implements ObjectClassScripting {

    public static ObjectClassScriptingFacade from(ConnectorContext context, String objectClass) {
        var schema = context.schema().objectClass(objectClass);
        if (schema == null) {
            throw new IllegalArgumentException("No such object class: " + objectClass);
        }
        var handler = context.handlerFor(schema.objectClass());
        return new ObjectClassScriptingFacade(context, schema, handler);
    }

    @Override
    public BaseObjectClassDefinition<BaseAttributeDefinition> definition() {
        return schema;
    }


    public void search(Filter filter, ResultsHandler consumer, OperationOptions options) {
        handler.checkSupported(ObjectSearchOperation.class).executeQuery(rest, filter, consumer, options);
    }
}
