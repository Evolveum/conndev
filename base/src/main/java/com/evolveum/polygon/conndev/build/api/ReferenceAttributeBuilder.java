/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.build.api;

import groovy.lang.Closure;
import org.identityconnectors.framework.common.objects.AttributeInfo;

public interface ReferenceAttributeBuilder extends AttributeBuilder<AttributeBuilder, P> {

    AttributeInfo.RoleInReference SUBJECT = AttributeInfo.RoleInReference.SUBJECT;
    AttributeInfo.RoleInReference OBJECT = AttributeInfo.RoleInReference.OBJECT;

    ReferenceAttributeBuilder objectClass(String objectClass);

    ReferenceAttributeBuilder subtype(String subtype);

    ReferenceAttributeBuilder role(String role);

    ReferenceAttributeBuilder role(AttributeInfo.RoleInReference role);

    interface Delegator extends ReferenceAttributeBuilder {

        ReferenceAttributeBuilder delegate();

        @Override
        default ReferenceAttributeBuilder objectClass(String objectClass) {
            return delegate().objectClass(objectClass);
        }

        @Override
        default ReferenceAttributeBuilder subtype(String subtype) {
            return delegate().subtype(subtype);
        }

        @Override
        default ReferenceAttributeBuilder role(String role) {
            return delegate().role(role);
        }

        @Override
        default ReferenceAttributeBuilder role(AttributeInfo.RoleInReference role) {
            return delegate().role(role);
        }

        @Override
        default AttributeBuilder<AttributeBuilder, P> readable(boolean readable) {
            return delegate().readable(readable);
        }

        @Override
        default AttributeBuilder<AttributeBuilder, P> required(boolean required) {
            return delegate().required(required);
        }

        @Override
        default void complexType(String objectClass) {
            delegate().complexType(objectClass);
        }

        @Override
        default AttributeBuilder<AttributeBuilder, P> returnedByDefault(boolean returnedByDefault) {
            return delegate().returnedByDefault(returnedByDefault);
        }

        @Override
        default AttributeBuilder<AttributeBuilder, P> multiValued(boolean multiValued) {
            return delegate().multiValued(multiValued);
        }

        @Override
        default AttributeBuilder<AttributeBuilder, P> creatable(boolean creatable) {
            return delegate().creatable(creatable);
        }

        @Override
        default AttributeBuilder<AttributeBuilder, P> updateable(boolean updateable) {
            return delegate().updateable(updateable);
        }

        @Override
        default AttributeBuilder<AttributeBuilder, P> description(String description) {
            return delegate().description(description);
        }

        @Override
        default void emulated(boolean emulated) {
            delegate().emulated(emulated);
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
        default AttributeBuilder<AttributeBuilder, P> protocolName(String protocolName) {
            return delegate().protocolName(protocolName);
        }

        @Override
        default AttributeBuilder<AttributeBuilder, P> remoteName(String remoteName) {
            return delegate().remoteName(remoteName);
        }

        @Override
        default AttributeBuilder<AttributeBuilder, P> jsonType(String jsonType) {
            return delegate().jsonType(jsonType);
        }

        @Override
        default AttributeBuilder<AttributeBuilder, P> openApiFormat(String openapiFormat) {
            return delegate().openApiFormat(openapiFormat);
        }

        @Override
        default AttributeBuilder<AttributeBuilder, P> updatable(boolean updatable) {
            delegate().updatable(updatable);
            return this;
        }
    }
}
