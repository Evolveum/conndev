/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.build.api;

import groovy.lang.Closure;
import org.identityconnectors.framework.common.objects.AttributeInfo;

public interface ReferenceAttributeBuilder<B extends ReferenceAttributeBuilder<B, A, P>, A extends AttributeBuilder<? super B, P>, P> extends AttributeBuilder<B, P> {

    AttributeInfo.RoleInReference SUBJECT = AttributeInfo.RoleInReference.SUBJECT;
    AttributeInfo.RoleInReference OBJECT = AttributeInfo.RoleInReference.OBJECT;

    B objectClass(String objectClass);

    B subtype(String subtype);

    B role(String role);

    B role(AttributeInfo.RoleInReference role);

    interface Delegator<B extends ReferenceAttributeBuilder<B, A,  P>,  A extends AttributeBuilder<? super B, P>, P> extends ReferenceAttributeBuilder<B, A,  P>  {

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

        @Override
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

    @SuppressWarnings("unchecked")
    default A asAttribute() {
        return (A) this;
    }
}
