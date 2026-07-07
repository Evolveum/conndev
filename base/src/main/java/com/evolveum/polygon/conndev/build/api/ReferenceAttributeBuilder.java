/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.build.api;

import groovy.lang.Closure;
import org.identityconnectors.framework.common.objects.AttributeInfo;

/**
 * Refinement of {@link AttributeBuilder} for reference (linking) attributes.
 *
 * <p>Reference attributes describe links between ConnId object classes, for example
 * a "groups" attribute on a User object that references Group objects. This interface
 * adds reference-specific configuration: target object class, subtype, and role within
 * the relationship (subject vs. object).</p>
 *
 * @param <B> The concrete builder type (self-type for CRTP)
 * @param <A> The parent attribute builder type
 * @param <P> The protocol type (e.g. JsonNode for REST/SCIM)
 */
public interface ReferenceAttributeBuilder<B extends ReferenceAttributeBuilder<B, A, P>, A extends AttributeBuilder<? super B, P>, P> extends AttributeBuilder<B, P> {

    AttributeInfo.RoleInReference SUBJECT = AttributeInfo.RoleInReference.SUBJECT;
    AttributeInfo.RoleInReference OBJECT = AttributeInfo.RoleInReference.OBJECT;

/**
 * Specifies the target ConnectId object class for the reference.
 *
 * @param objectClass the target object class value
 * @return the current instance for method chaining
 */
B objectClass(String objectClass);

/**
 * Specifies a subtype qualifier for the reference attribute.
 *
 * @param subtype the subtype value
 * @return the current instance for method chaining
 */
B subtype(String subtype);

/**
 * Specifies the role for the reference by role name.
 *
 * @param role the role name
 * @return the current instance for method chaining
 */
B role(String role);

/**
 * Specifies the role for the reference.
 *
 * @param role the role enum value
 * @return the current instance for method chaining
 */
B role(AttributeInfo.RoleInReference role);

    /**
     * A proxy interface that delegates all reference attribute builder methods to an underlying instance.
     *
     * Useful for building reference attributes in a deferred or wrapped manner.
     */
    interface Delegator<B extends ReferenceAttributeBuilder<B, A,  P>,  A extends AttributeBuilder<? super B, P>, P> extends ReferenceAttributeBuilder<B, A,  P>  {
        /**
         * Returns the underlying delegate reference builder.
         *
         * @return the delegate instance
         */
        B delegate();

        @Override
        default B objectClass(String objectClass) {
            return delegate().objectClass(objectClass);
        }

        @Override
        default B subtype(String subtype) {
            return delegate().subtype(subtype);
        }

        @Override
        default B role(String role) {
            return delegate().role(role);
        }

        @Override
        default B role(AttributeInfo.RoleInReference role) {
            return delegate().role(role);
        }

        @Override
        default B readable(boolean readable) {
            return delegate().readable(readable);
        }

        @Override
        default B required(boolean required) {
            return delegate().required(required);
        }

        @Override
        default B complexType(String objectClass) {
            return delegate().complexType(objectClass);
        }

        @Override
        default B returnedByDefault(boolean returnedByDefault) {
            return delegate().returnedByDefault(returnedByDefault);
        }

        @Override
        default B multiValued(boolean multiValued) {
            return delegate().multiValued(multiValued);
        }

        default B creatable(boolean creatable) {
            return delegate().creatable(creatable);
        }

        @Override
        default B updateable(boolean updatable) {
            return delegate().updateable(updatable);
        }

        @Override
        default B description(String description) {
            return delegate().description(description);
        }

        @Override
        default B emulated(boolean emulated) {
            return delegate().emulated(emulated);
        }

        @Override
        default JsonMapping json() {
            return delegate().json();
        }

        default JsonMapping json(Closure<?> closure) {
            return delegate().json(closure);
        }

        @Override
        default ConnIdMapping connId() {
            return delegate().connId();
        }

        @Override
        default B protocolName(String protocolName) {
            return delegate().protocolName(protocolName);
        }

        @Override
        default B remoteName(String remoteName) {
            return delegate().remoteName(remoteName);
        }

        @Override
        default B jsonType(String jsonType) {
            return delegate().jsonType(jsonType);
        }

        @Override
        default B openApiFormat(String openapiFormat) {
            return delegate().openApiFormat(openapiFormat);
        }

        @Override
        default B updatable(boolean updatable) {
            return delegate().updatable(updatable);
        }
    }

    /**
     * Casts this reference attribute builder to the parent attribute type.
     *
     * @return {@code this} cast to the parent attribute builder type
     */
    @SuppressWarnings("unchecked")
    default A asAttribute() {
        return (A) this;
    }
}
