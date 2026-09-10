/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.groovy.api;

import com.evolveum.polygon.conndev.annotations.Groovy;
import com.evolveum.polygon.conndev.build.api.FilterBuilder;
import com.evolveum.polygon.conndev.logging.ConnDevLog;
import com.evolveum.polygon.conndev.schema.BaseAttributeDefinition;
import com.evolveum.polygon.conndev.schema.BaseObjectClassDefinition;

public interface BaseScriptContext {

    /**
     * Returns the logging facade for the current script execution.
     *
     * <p>Events logged through the facade carry the caller location, so in development mode
     * they are attributed to the exact script line.
     *
     * @return the logging facade
     */
    @Groovy.Convenience
    default ConnDevLog log() {
        return ConnDevLog.of(getClass());
    }


    /**
     * Returns the object class definition for the current search operation.
     *
     * @return the object class definition
     */
    @Groovy.Convenience
    BaseObjectClassDefinition<? extends BaseAttributeDefinition> definition();


    /**
     * Creates a filter builder for the specified attribute using its protocol name.
     * This method is used to create filters for attributes in custom search implementations.
     *
     * @param protocolName the protocol name of the attribute to filter on
     * @return a filter builder for the specified attribute
     * @throws IllegalArgumentException if the attribute is not found
     */
    @Groovy.Convenience
    default FilterBuilder.AttributeFilterBuilder attributeFilter(String protocolName) {
        var attribute = definition().attributeFromProtocolName(protocolName);
        if (attribute == null) {
            throw new IllegalArgumentException("Unknown attribute: " + protocolName);
        }

        return FilterBuilder.forAttribute(attribute.connId().getName());
    }

    /**
     * Returns an object class handler for the specified object class name.
     * This allows access to other object classes during a custom search implementation.
     *
     * @param name the name of the object class
     * @return an object class handler for the specified object class
     */
    @Groovy.Convenience
    ObjectClassScripting objectClass(String name);

}
