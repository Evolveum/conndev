/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.schema;

import com.evolveum.polygon.conndev.build.api.RelationshipBuilder;
import com.evolveum.polygon.conndev.concepts.GroovyClosures;
import com.evolveum.polygon.conndev.concepts.SourceLocation;
import org.identityconnectors.framework.common.exceptions.ConfigurationException;
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
        return new BaseParticipantBuilder(this, schemaBuilder.objectClass(objectClass), SourceLocation.capture());
    }

    @Override
    public Participant<B,P> subject(
            String objectClass, @Script.Initialization Closure<?> closure) {
        if (subject == null) {
            subject = participant(objectClass);
        }
        return GroovyClosures.callAndReturnDelegate(closure, subject);
    }

    @Override
    public Participant<B,P> object(
            String objectClass, @Script.Initialization Closure<?> closure) {
        if (object == null) {
            object = participant(objectClass);
        }
        return GroovyClosures.callAndReturnDelegate(closure, object);
    }
    */

    /**
     * Runs after all participant configuration has been applied.
     *
     * <p>Configures cross-references between subject and object: each side's reference
     * attribute is pointed to the opposite participant's object class, and the {@link AttributeInfo.RoleInReference}
     * is set (SUBJECT for the subject side, OBJECT for the object side).</p>
     *
     * <p>Each participant must declare its reference attribute via {@code attribute('<name>')};
     * a participant without one is a configuration error, reported with the relationship name
     * and the participant's source location instead of surfacing as a
     * {@code NullPointerException}.</p>
     *
     * @throws ConfigurationException if either participant declares no reference attribute
     */
    @Override
    public void afterExecution() {
        if (subject != null && object != null) {
            // configure side mappings.
            var subjectAttribute = requireParticipantAttribute(subject, "subject");
            var objectAttribute = requireParticipantAttribute(object, "object");

            subjectAttribute.objectClass(object.objectClass().getObjectClassValue());
            subjectAttribute.role(AttributeInfo.RoleInReference.SUBJECT);

            objectAttribute.objectClass(subject.objectClass().getObjectClassValue());
            objectAttribute.role(AttributeInfo.RoleInReference.OBJECT);

        }
    }

    /**
     * Returns the participant's reference attribute binding, failing fast when it was not declared.
     *
     * @param participant the participant to check
     * @param side the participant's role within the relationship ({@code "subject"} or {@code "object"})
     * @return the participant's reference attribute binding
     * @throws ConfigurationException if the participant declares no reference attribute
     */
    private B requireParticipantAttribute(BaseParticipantBuilder<B, P> participant, String side) {
        var attribute = participant.attribute();
        if (attribute == null) {
            throw new ConfigurationException(
                    missingAttributeMessage(name, side, participant.objectClass().getObjectClassValue(), participant.location()));
        }
        return attribute;
    }

    /**
     * Builds the configuration error message for a relationship participant that declares no
     * reference attribute.
     *
     * @param relationshipName the relationship's name
     * @param side the participant's role within the relationship ({@code "subject"} or {@code "object"})
     * @param objectClass the object class value of the offending participant
     * @param location the script location of the participant declaration (included when known)
     * @return the error message
     */
    public static String missingAttributeMessage(String relationshipName, String side, String objectClass, SourceLocation location) {
        return "Relationship '" + relationshipName + "': the " + side + " participant ('" + objectClass
                + "') declares no reference attribute; declare one with attribute('<name>') inside "
                + side + "('" + objectClass + "')"
                + (location != null && location != SourceLocation.UNKNOWN ? " [at " + location + "]" : "");
    }
}
