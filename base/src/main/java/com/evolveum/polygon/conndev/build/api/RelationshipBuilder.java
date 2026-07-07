/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.build.api;

import com.evolveum.polygon.conndev.spi.AttributeResolver;
import groovy.lang.Closure;
import groovy.lang.DelegatesTo;

/**
 * Builder for defining relationships between object classes.
 *
 * <p>Relationships model links between ConnId object classes, analogous to SCIM2 relationships.
 * Each relationship has a subject and an object participant, and configures a reference
 * attribute that acts as the emulated link (resolved at runtime).</p>
 *
 * <pre>{@code
 * relationship("userGroups") {
 *     subject("User") {
 *         attribute("groups") {
 *             objectClass("Group")
 *         }
 *         owner(true)
 *     }
 *     object("Group") {
 *         owner(false)
 *     }
 * }
 * }</pre>
 *
 * @param <B> The reference builder type (self-type for CRTP)
 * @param <P> The protocol type
 */
public interface RelationshipBuilder<B extends RelationshipBuilder.Reference<B,?,P>,P> {

    /**
     * Declares an object class as the subject (semantic primary) of this relationship.
     *
     * @param objectClass the object class of the relationship subject
     * @param closure a closure that configures the subject part of the relationship
     * @return a builder for customizing the subject participant
     */
    Participant<B, P> subject(String objectClass, @DelegatesTo(value = Participant.class, strategy = Closure.DELEGATE_ONLY) Closure<?> closure);

    /**
     * Declares an object class as an object participant of this relationship.
     *
     * @param objectClass the object class of the relationship object
     * @param closure a closure that configures the object participant
     * @return a builder for customizing the object participant
     */
    Participant<B, P> object(String objectClass, @DelegatesTo(value = Participant.class, strategy = Closure.DELEGATE_ONLY) Closure<?> closure);

    /**
     * Participant side of a relationship (either subject or object).
     *
     * @param <B> The reference builder type (CRTP self-type)
     * @param <P> The protocol value type
     */
    interface Participant<B extends Reference<B, ?, P>, P> {

        /**
         * Returns the reference attribute for this participant.
         *
         * @param name the attribute name
         * @return the reference builder
         */
        B attribute(String name);

        /**
         * Configures the reference attribute with a closure.
         *
         * @param name the attribute name
         * @param closure a closure that configures the {@link Reference}
         * @return the reference builder
         */
        B attribute(String name, @DelegatesTo(value = Reference.class, strategy = Closure.DELEGATE_ONLY) Closure<?> closure);

        /**
         * Checks if this participant is the relationship owner.
         *
         * @return true if the participant is the owner
         */
        boolean owner();

        /**
         * Sets the relationship owner status for the participant.
         *
         * Usually relationships may have owner irrespective of subject - object relation.
         * The owner is the actual participant which stores relations, and is used to modify relations:
         * in case of LDAP it could be group. In case of midPoint it is user, even if in both user is subject and group is object.
         *
         * @param owner true if the participant is the owner of relationship, false otherwise
         * @return this participant instance to allow chained invocations
         */
        B owner(boolean owner);
    }

    /**
     * A reference attribute within a relationship participant.
     *
     * <p>Extends {@link ReferenceAttributeBuilder} to add runtime attribute resolution
     * via {@link AttributeResolverBuilder}. The reference is automatically marked as
     * emulated.</p>
     */
    interface Reference<B extends ReferenceAttributeBuilder<B,A,P>, A extends AttributeBuilder<? super B, P>, P> extends ReferenceAttributeBuilder<B, A, P> {

        /**
         * Adds an attribute resolver with custom behavior.
         *
         * <p>The attribute will be marked as emulated (true). Values are provided
         * by the resolver defined in the closure.</p>
         *
         * @param closure the closure defining custom resolution behavior
         * @return an {@link AttributeResolverBuilder} for configuring the resolver
         */
        AttributeResolverBuilder resolver(@DelegatesTo(value = AttributeResolverBuilder.class, strategy = Closure.DELEGATE_ONLY) Closure<?> closure);
    }
}
