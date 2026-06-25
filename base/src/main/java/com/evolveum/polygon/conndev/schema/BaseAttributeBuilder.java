/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.schema;

import com.evolveum.polygon.conndev.build.AttributeResolverBuilder;
import com.evolveum.polygon.conndev.build.ReferenceAttributeBuilder;
import com.evolveum.polygon.conndev.concepts.Deferred;
import com.evolveum.polygon.conndev.concepts.GroovyClosures;
import com.evolveum.polygon.conndev.groovy.ScriptedSingleAttributeResolverBuilder;
import groovy.lang.Closure;
import org.identityconnectors.framework.common.objects.AttributeInfo;
import org.identityconnectors.framework.common.objects.Name;
import org.identityconnectors.framework.common.objects.Uid;

public class BaseAttributeBuilder extends AbstractAttributeBuilder implements ReferenceAttributeBuilder {

    public Deferred.Settable<BaseAttributeDefinition> deffered = Deferred.settable();
    private String referencedObjectClass;
    private boolean isReference = false;
    ScriptedSingleAttributeResolverBuilder resolverBuilder;

    public BaseAttributeBuilder(BaseObjectClassDefinitionBuilder restObjectClassBuilder, String name) {
        super(restObjectClassBuilder, name);
    }

    @Override
    public BaseAttributeBuilder objectClass(String objectClass) {
        isReference = true;
        this.referencedObjectClass = objectClass;
        this.connIdBuilder.setReferencedObjectClassName(objectClass);
        return this;
    }

    @Override
    public BaseAttributeBuilder subtype(String subtype) {
        this.connIdBuilder.setSubtype(subtype);
        return this;
    }

    @Override
    public BaseAttributeBuilder role(String role) {
        this.isReference = true;
        this.connIdBuilder.setRoleInReference(role);
        return this;
    }

    @Override
    public BaseAttributeBuilder role(AttributeInfo.RoleInReference role) {
        return role(role.toString());
    }


    public boolean isReference() {
        return isReference;
    }

    /**
     * Builds and returns a {@code RestAttribute} instance with the specified attributes.
     *
     * @return a new {@code RestAttribute} instance configured with the current settings
     */
    public BaseAttributeDefinition build() {
        // FIXME: Could this be part of ConnID schema contributor?
        if (Uid.NAME.equals(connIdName)) {
            connId().type(String.class);
        }
        if (Name.NAME.equals(connIdName)) {
            connId().type(String.class);
        }
        return new BaseAttributeDefinition(this);
    }

    public AttributeResolverBuilder resolver(Closure<?> closure) {
        this.emulated = true;
        this.resolverBuilder = new ScriptedSingleAttributeResolverBuilder(objectClass.name(), deffered);
        GroovyClosures.callAndReturnDelegate(closure, resolverBuilder);
        return resolverBuilder;
    }
}
