/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.schema;

import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.Schema;

import java.util.Map;

public class BaseSchema {

    private final Schema connIdSchema;
    private final Map<ObjectClass, BaseObjectClassDefinition> objectClasses;

    public BaseSchema(Schema connIdSchema, Map<ObjectClass, BaseObjectClassDefinition> objectClasses) {
        this.connIdSchema = connIdSchema;
        this.objectClasses = objectClasses;
    }

    public Schema connIdSchema() {
        return connIdSchema;
    }

    public BaseObjectClassDefinition objectClass(String name) {
        return objectClasses.get(new ObjectClass(name));
    }
}
