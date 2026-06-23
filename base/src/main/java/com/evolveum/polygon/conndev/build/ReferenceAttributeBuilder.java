/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.build;

import groovy.lang.Closure;
import org.identityconnectors.framework.common.objects.AttributeInfo;

public interface ReferenceAttributeBuilder extends AttributeBuilder {

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
        default AttributeBuilder readable(boolean readable) {
            return delegate().readable(readable);
        }

        @Override
        default AttributeBuilder required(boolean required) {
            return delegate().required(required);
        }

        @Override
        default void complexType(String objectClass) {
            delegate().complexType(objectClass);
        }

        @Override
        default AttributeBuilder returnedByDefault(boolean returnedByDefault) {
            return delegate().returnedByDefault(returnedByDefault);
        }

        @Override
        default AttributeBuilder multiValued(boolean multiValued) {
            return delegate().multiValued(multiValued);
        }

        @Override
        default AttributeBuilder creatable(boolean creatable) {
            return delegate().creatable(creatable);
        }

        @Override
        default AttributeBuilder updateable(boolean updateable) {
            return delegate().updateable(updateable);
        }

        @Override
        default AttributeBuilder description(String description) {
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
        default AttributeBuilder protocolName(String protocolName) {
            return delegate().protocolName(protocolName);
        }

        @Override
        default AttributeBuilder remoteName(String remoteName) {
            return delegate().remoteName(remoteName);
        }

        @Override
        default AttributeBuilder jsonType(String jsonType) {
            return delegate().jsonType(jsonType);
        }

        @Override
        default AttributeBuilder openApiFormat(String openapiFormat) {
            return delegate().openApiFormat(openapiFormat);
        }

        @Override
        default AttributeBuilder updatable(boolean updatable) {
            delegate().updatable(updatable);
            return this;
        }
    }
}
