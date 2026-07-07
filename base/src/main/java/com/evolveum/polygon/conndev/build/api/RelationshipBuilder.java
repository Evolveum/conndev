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

public interface RelationshipBuilder<B extends RelationshipBuilder.Reference<B,?,P>,P> {


    /**
     * Declares object class as a subject of this relationship
     *
     * @param objectClass Object Class of subject of this relationship (semantic owner of relation)
     * @param closure Groovy closure which configures subject-part of relation
     * @return Builder which allows to customize subject part of relationship
     */
    Participant<B, P> subject(String objectClass, @DelegatesTo(value = Participant.class, strategy = Closure.DELEGATE_ONLY) Closure<?> closure);


    /**
     * Declares an object class as a participant of this relationship.
     *
     * @param objectClass Object class of the object.
     * @param closure Groovy closure to configure the participant part of the relationship.
     * @return Builder for customizing the participant part of the relationship.
     */
    Participant<B, P> object(String objectClass, @DelegatesTo(value = Participant.class, strategy = Closure.DELEGATE_ONLY) Closure<?> closure);



    interface Participant<B extends Reference<B, ?, P>, P> {

        B attribute(String name);

        B attribute(String name, @DelegatesTo(value = Reference.class, strategy = Closure.DELEGATE_ONLY) Closure<?> closure);

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

    interface Reference<B extends ReferenceAttributeBuilder<B,A,P>, A extends AttributeBuilder<? super B, P>, P> extends ReferenceAttributeBuilder<B, A, P> {

        /**
         * Adds an attribute resolver with custom behavior defined.
         *
         * The attribute will be marked as {@link #emulated(boolean)} true.
         * The values for attribute will be provided by defined attribute resolver.
         *
         *
         * @param closure The closure defining custom behavior for the attribute resolver.
         * @return A new instance of {@link AttributeResolver} configured according to the closure's specifications.
         */
        AttributeResolverBuilder resolver(@DelegatesTo(value = AttributeResolverBuilder.class, strategy = Closure.DELEGATE_ONLY) Closure<?> closure);
    }
}
