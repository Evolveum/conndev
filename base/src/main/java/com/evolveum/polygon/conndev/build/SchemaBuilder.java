/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.build;

import com.evolveum.polygon.conndev.annotations.Script;
import groovy.lang.Closure;
import groovy.lang.DelegatesTo;

public interface SchemaBuilder {


    ObjectClassSchemaBuilder objectClass(String name);

    ObjectClassSchemaBuilder objectClass(String name, @DelegatesTo(ObjectClassSchemaBuilder.class) @Script.Initialization Closure<?> closure);

    RelationshipBuilder relationship(String name, @DelegatesTo(RelationshipBuilder.class) @Script.Initialization Closure<?> closure);

}
