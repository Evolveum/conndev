/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.build;

import com.evolveum.polygon.conndev.annotations.Script;
import com.evolveum.polygon.conndev.concepts.GroovyClosures;
import groovy.lang.Closure;
import groovy.lang.DelegatesTo;

public interface SchemaBuilder {


    ObjectClassSchemaBuilder objectClass(String name);

    default ObjectClassSchemaBuilder objectClass(String name,
                                         @Script.Initialization
                                         @DelegatesTo(ObjectClassSchemaBuilder.class)  Closure<?> closure) {
        return GroovyClosures.callAndReturnDelegate(closure, objectClass(name));
    }

    RelationshipBuilder relationship(String name,
                                     @Script.Initialization
                                     @DelegatesTo(RelationshipBuilder.class) Closure<?> closure);

}
