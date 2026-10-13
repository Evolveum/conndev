/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.schema;

import com.evolveum.polygon.conndev.annotations.Script;
import com.evolveum.polygon.conndev.build.api.AttributeBuilder;
import com.evolveum.polygon.conndev.build.api.ReferenceAttributeBuilder;
import com.evolveum.polygon.conndev.build.api.RelationshipBuilder;
import com.evolveum.polygon.conndev.concepts.GroovyClosures;
import com.evolveum.polygon.conndev.concepts.SourceLocation;
import groovy.lang.Closure;
import groovy.lang.DelegatesTo;
import org.identityconnectors.framework.common.exceptions.ConfigurationException;
import org.identityconnectors.framework.common.objects.ObjectClass;

/**
 * Abstract base implementation of {@link RelationshipBuilder.Participant} for building
 * participant definitions within SCIM2 relationships.
 * <p>
 * This class provides shared functionality for both owner and non-owner participants
 * of a relationship, tracking the associated object class, parent relationship builder,
 * and optional attribute binding. Concrete implementations typically represent either
 * the {@literal "owner"} side or the {@literal "member"} side of a relationship.
 *
 * @param <B> the type of relationship builder reference for this participant
 * @param <P> the parent relationship builder type
 */
public abstract class BaseParticipantBuilder<B extends RelationshipBuilder.Reference<B,?,P>, P> implements RelationshipBuilder.Participant<B,P> {

    /** The object class definition builder to which this participant belongs. */
    private final BaseObjectClassDefinitionBuilder objectClass;

    /** The parent relationship builder that owns this participant. */
    private final B parent;

    /** The attribute binding for this participant; lazily initialized on first access. */
    private B attribute;

    /** Whether this participant is the owner of the relationship. */
    private Boolean owner;

    /** The script location of the participant's {@code subject(...)} / {@code object(...)} declaration. */
    private final SourceLocation location;

    /**
     * Constructs a new participant builder.
     *
     * @param relationshipBuilder the parent relationship builder
     * @param targetClass the object class definition builder for this participant
     * @param location the script location of the participant declaration (used in configuration
     *                 error messages); pass {@link SourceLocation#UNKNOWN} when unknown
     */
    public BaseParticipantBuilder(B relationshipBuilder, BaseObjectClassDefinitionBuilder targetClass, SourceLocation location) {
        this.parent = relationshipBuilder;
        this.objectClass = targetClass;
        this.location = location != null ? location : SourceLocation.UNKNOWN;
    }

    /**
     * Returns the script location of the participant declaration (never {@code null}).
     *
     * @return the captured location, or {@link SourceLocation#UNKNOWN}
     */
    public SourceLocation location() {
        return location;
    }

    /**
     * Stores the attribute binding created by {@link #attribute(String)} so the no-arg
     * {@link #attribute()} accessor — and the relationship's post-execution wiring — see it.
     *
     * @param binding the reference attribute binding for this participant
     */
    protected void setReferenceAttribute(B binding) {
        this.attribute = binding;
    }

    /**
     * Returns the current attribute binding for this participant, or {@code null} if not yet set.
     *
     * @return the attribute builder, or {@code null}
     */
    B attribute() {
        return attribute;
    }

    /**
     * Creates or returns the attribute reference builder for a given attribute name.
     * The attribute is typed as {@link ConnectorObjectReference} and tagged with the
     * parent relationship name as a subtype.
     * <p>
     * Concrete implementations provide the body for this abstract method.
     *
     * @param name the attribute name
     * @return the reference builder for the attribute
     */
    @Override
    public abstract B attribute(String name);

    /**
     * Configures the attribute for this participant using the given closure.
     * <p>
     * This method creates the attribute via {@link #attribute(String)} if needed,
     * then executes the closure with the attribute builder as its delegate, returning
     * the builder result. This enables a Groovy DSL style such as:
     * <pre>{@code
     * attribute("members") {
     *     displayName("Member Users")
     * }
     * }</pre>
     *
     * @param name the attribute name
     * @param closure the configuration closure with the attribute builder as delegate
     * @return this participant builder for chaining
     */
    @Override
    public B attribute(
            String name,
            @Script.Initialization
            @DelegatesTo(value = RelationshipBuilder.Reference.class, strategy = Closure.DELEGATE_ONLY)
            Closure<?> closure) {
        return GroovyClosures.callAndReturnDelegate(closure, attribute(name));
    }

    /**
     * Returns whether this participant is the owner of the relationship.
     *
     * @return {@code true} if this participant is the owner
     */
    @Override
    public boolean owner() {
        return Boolean.TRUE.equals(this.owner);
    }

    /**
     * Marks this participant as the owner of the relationship.
     *
     * <p>The participant's reference attribute must already be declared — {@code owner(...)}
     * configures that attribute, so declaring it later is a configuration error reported with
     * the participant's source location instead of a {@code NullPointerException}.</p>
     *
     * @param owner {@code true} to mark the participant as the owner
     * @return the participant's reference builder for chaining
     * @throws ConfigurationException if no reference attribute has been declared yet
     */
    @Override
    public B owner(boolean owner) {
        this.owner = owner;
        if (attribute == null) {
            throw new ConfigurationException(ownerRequiresAttributeMessage());
        }
        return attribute;
    }

    /**
     * Builds the configuration error message for {@link #owner(boolean)} called before the
     * participant's reference attribute is declared.
     *
     * @return the message, including the participant's source location when known
     */
    private String ownerRequiresAttributeMessage() {
        return "owner() requires the reference attribute to be declared first: declare attribute('<name>') "
                + "in the " + objectClass.objectClass().getObjectClassValue() + " participant before owner(...)"
                + (location != SourceLocation.UNKNOWN ? " [at " + location + "]" : "");
    }

    /**
     * Returns the object class associated with this participant.
     *
     * @return the participant's object class
     */
    public ObjectClass objectClass() {
        return objectClass.objectClass();
    }

    /**
     * Abstract base for reference builders used within relationship participant definitions.
     * <p>
     * This class wraps a delegate {@link ReferenceAttributeBuilder} and provides access to it
     * via the {@link Delegator} interface. Concrete implementations handle attribute-specific
     * configuration for relationship members.
     *
     * @param <B> the type of the delegate reference attribute builder
     * @param <A> the type of the attribute builder super-type
     * @param <P> the parent type
     */
    abstract static class ReferenceBuilder<B extends ReferenceAttributeBuilder<B, A, P>, A extends AttributeBuilder<? super B, P>, P> implements RelationshipBuilder.Reference<B,A,P>, ReferenceAttributeBuilder.Delegator<B,A, P> {

        /** The delegate reference attribute builder. */
        private final B delegate;

        /**
         * Constructs a new ReferenceBuilder wrapping the given delegate.
         *
         * @param delegate the reference attribute builder delegate
         */
        public ReferenceBuilder(B delegate) {
            this.delegate = delegate;
        }

        /**
         * Returns the delegate reference attribute builder.
         *
         * @return the delegate builder
         */
        @Override
        public B delegate() {
            return delegate;
        }

        /*
         * TODO: Enable resolver() when the underlying ReferenceAttributeBuilder supports it.
         * A resolver closure can be used to customize how attribute values are resolved
         * for relationship members, e.g. filtering, transformation, or custom lookup logic.
         *
        @Override
        public B resolver(Closure<?> closure) {
            return delegate.resolver(closure);
        }
        */
    }
}
