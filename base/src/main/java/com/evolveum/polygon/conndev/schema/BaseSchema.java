/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.schema;

import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.Schema;

import java.util.Collection;
import java.util.Map;

/**
 * Schema representation holding the ConnId schema object and a map of
 * all defined object class definitions for a connector.
 */
public class BaseSchema<O extends BaseObjectClassDefinition<?>> {

    /** The underlying ConnId {@link Schema} that can be returned by the connector. */
    private final Schema connIdSchema;
    /** Map containing all object class definitions keyed by their ConnId ObjectClass. */
    private final Map<ObjectClass, O> objectClasses;

    /**
     * Constructs a new BaseSchema.
     *
     * @param connIdSchema the ConnId Schema object
     * @param objectClasses the map of object class definitions
     */
    public BaseSchema(Schema connIdSchema, Map<ObjectClass, O> objectClasses) {
        this.connIdSchema = connIdSchema;
        this.objectClasses = objectClasses;
    }

    /**
     * Returns the underlying ConnId Schema object.
     *
     * @return the ConnId Schema
     */
    public Schema connIdSchema() {
        return connIdSchema;
    }

    /**
     * Returns the object class definition for the given class name.
     *
     * @param name the object class name
     * @return the matching BaseObjectClassDefinition, or null if not found
     */
    public O objectClass(String name) {
        return objectClasses.get(new ObjectClass(name));
    }

    public O objectClass(ObjectClass objectClass) {
        return objectClasses.get(objectClass);
    }

    /**
     * Returns all defined object class definitions.
     *
     * @return collection of all BaseObjectClassDefinition instances
     */
    public Collection<O> objectClasses() {
        return objectClasses.values();
    }
}
