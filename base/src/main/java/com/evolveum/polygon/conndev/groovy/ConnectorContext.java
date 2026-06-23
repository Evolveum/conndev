/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.groovy;

import com.evolveum.polygon.conndev.api.ContextLookup;
import com.evolveum.polygon.conndev.spi.ObjectClassHandler;
import com.evolveum.polygon.conndev.concepts.RetrievableContext;
import com.evolveum.polygon.conndev.schema.BaseSchema;
import org.identityconnectors.framework.common.objects.ObjectClass;

import java.util.Map;

public interface ConnectorContext extends ContextLookup, RetrievableContext {


    ObjectClassHandler handlerFor(ObjectClass objectClass);


    BaseSchema schema();

    boolean getDevelopmentMode();

    @Override
    default <T extends RetrievableContext> T get(Class<T> contextType) throws IllegalStateException {
        var ret = getUnchecked(contextType);
        if (ret == null) {
            throw new IllegalStateException(String.format("No context found for type %s", contextType.getName()));
        }
        return ret;
    }

    /**
     * Retrieves a context of the specified type without performing null validation.
     * Subclasses must implement this method to provide the corresponding context instance.
     *
     * @param contextType The class object representing the type of context to retrieve.
     * @return The context instance of the specified type, or null if not found.
     */
    <T extends RetrievableContext> T getUnchecked(Class<T> contextType);
}
