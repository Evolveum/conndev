/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.schema;

import com.evolveum.polygon.conndev.build.RelationshipBuilder;
import com.evolveum.polygon.conndev.concepts.GroovyClosures;
import groovy.lang.Closure;
import org.identityconnectors.framework.common.objects.AttributeInfo;

public class AbstractRelationshipBuilder implements RelationshipBuilder, GroovyClosures.ClosureExecutionAware {

    private final BaseSchemaBuilder schemaBuilder;

    final String name;
    BaseParticipantBuilder subject;
    BaseParticipantBuilder object;

    public AbstractRelationshipBuilder(String name, BaseSchemaBuilder schemaBuilder) {
        this.name = name;
        this.schemaBuilder = schemaBuilder;
    }

    private BaseParticipantBuilder participant(String objectClass) {
        return new BaseParticipantBuilder(this, schemaBuilder.objectClass(objectClass));
    }

    @Override
    public Participant subject(String objectClass, Closure<?> closure) {
        if (subject == null) {
            subject = participant(objectClass);
        }
        return GroovyClosures.callAndReturnDelegate(closure,subject);
    }

    @Override
    public Participant object(String objectClass, Closure<?> closure) {
        if (object == null) {
            object = participant(objectClass);
        }
        return GroovyClosures.callAndReturnDelegate(closure,object);
    }

    @Override
    public void afterExecution() {
        if (subject != null && object != null) {
            // configure side mappings.
            subject.attribute().objectClass(object.objectClass());
            subject.attribute().role(AttributeInfo.RoleInReference.SUBJECT);

            object.attribute().objectClass(subject.objectClass());
            object.attribute().role(AttributeInfo.RoleInReference.OBJECT);

        }
    }
}
