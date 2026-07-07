/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.schema;

import com.evolveum.polygon.conndev.build.api.ReferenceAttributeBuilder;
import com.evolveum.polygon.conndev.build.api.RelationshipBuilder;
import com.evolveum.polygon.conndev.concepts.GroovyClosures;
import groovy.lang.Closure;
import org.identityconnectors.framework.common.objects.AttributeInfo;

/**
 * Abstract base implementation of {@link RelationshipBuilder}.
 *
 * <p>Manages the subject and object participants of a relationship, providing the
 * infrastructure for configuring reference attributes and resolving side mappings
 * between the two object classes after construction.</p>
 *
 * @param <B> The reference builder type (CRTP self-type)
 * @param <P> The protocol value type
 */
public abstract class AbstractRelationshipBuilder<B extends RelationshipBuilder.Reference<B,?,P>, P> implements RelationshipBuilder<B,P>, GroovyClosures.ClosureExecutionAware {

    /**
     * The parent schema builder used to create object class references for participants.
     */
    private final BaseSchemaBuilder schemaBuilder;

    /**
     * The name of this relationship.
     */
    final String name;

    /**
     * The subject participant of this relationship (e.g., the "user" in a user-group relationship).
     */
    BaseParticipantBuilder<B,P> subject;

    /**
     * The object participant of this relationship (e.g., the "group" in a user-group relationship).
     */
    BaseParticipantBuilder<B,P> object;

    /**
     * Creates a new relationship builder with the given name under the specified schema builder.
     *
     * @param name the relationship name
     * @param schemaBuilder the parent schema builder
     */
    public AbstractRelationshipBuilder(String name, BaseSchemaBuilder schemaBuilder) {
        this.name = name;
        this.schemaBuilder = schemaBuilder;
    }

    /*
    private BaseParticipantBuilder<B,P> participant(String objectClass) {
        return new BaseParticipantBuilder(this, schemaBuilder.objectClass(objectClass));
    }

    @Override
    public Participant<B,P> subject(String objectClass, Closure<?> closure) {
        if (subject == null) {
            subject = participant(objectClass);
        }
        return GroovyClosures.callAndReturnDelegate(closure,subject);
    }

    @Override
    public Participant<B,P> object(String objectClass, Closure<?> closure) {
        if (object == null) {
            object = participant(objectClass);
        }
        return GroovyClosures.callAndReturnDelegate(closure,object);
    }
    */

    /**
     * Runs after all participant configuration has been applied.
     *
     * <p>Configures cross-references between subject and object: each side's reference
     * attribute is pointed to the opposite participant's object class, and the {@link AttributeInfo.RoleInReference}
     * is set (SUBJECT for the subject side, OBJECT for the object side).</p>
     */
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
