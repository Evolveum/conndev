/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.schema;

import com.evolveum.polygon.conndev.build.api.AttributeResolverBuilder;
import com.evolveum.polygon.conndev.build.api.ReferenceAttributeBuilder;
import com.evolveum.polygon.conndev.concepts.Deferred;
import com.evolveum.polygon.conndev.concepts.DefinitionValue;
import com.evolveum.polygon.conndev.concepts.GroovyClosures;
import com.evolveum.polygon.conndev.groovy.ScriptedSingleAttributeResolverBuilder;
import groovy.lang.Closure;
import org.identityconnectors.framework.common.objects.AttributeInfo;
import org.identityconnectors.framework.common.objects.Name;
import org.identityconnectors.framework.common.objects.Uid;

public abstract class BaseAttributeBuilder<B extends BaseAttributeBuilder<B, P>, P extends BaseAttributeDefinition> extends AbstractAttributeBuilder<B,P> implements ReferenceAttributeBuilder<B, P> {

    public Deferred.Settable<BaseAttributeDefinition> deffered = Deferred.settable();
    private String referencedObjectClass;
    String referencedAttribute;
    private boolean isReference = false;
    ScriptedSingleAttributeResolverBuilder resolverBuilder;

    public BaseAttributeBuilder(BaseObjectClassDefinitionBuilder restObjectClassBuilder, DefinitionValue<String> name) {
        super(restObjectClassBuilder, name);
    }

    @Override
    public B objectClass(String objectClass) {
        isReference = true;
        this.referencedObjectClass = objectClass;
        this.connIdBuilder.setReferencedObjectClassName(objectClass);
        return self();
    }

    @Override
    public B subtype(String subtype) {
        this.connIdBuilder.setSubtype(subtype);
        return self();
    }

    /** The attribute of the referenced object class this reference points to (e.g. a FK target column). */
    public BaseAttributeBuilder referencedAttribute(String referencedAttribute) {
        this.referencedAttribute = referencedAttribute;
        return this;
    }

    @Override
    public B role(String role) {
        this.isReference = true;
        this.connIdBuilder.setRoleInReference(role);
        return self();
    }

    @Override
    public B role(AttributeInfo.RoleInReference role) {
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
    public P build() {
        // FIXME: Could this be part of ConnID schema contributor?
        if (Uid.NAME.equals(connIdName)) {
            connId().type(String.class);
        }
        if (Name.NAME.equals(connIdName)) {
            connId().type(String.class);
        }
        return (P) new BaseAttributeDefinition(this);
    }

    public AttributeResolverBuilder resolver(Closure<?> closure) {
        this.emulated = DefinitionValue.detected(true);
        this.resolverBuilder = new ScriptedSingleAttributeResolverBuilder(objectClass.name(), deffered);
        GroovyClosures.callAndReturnDelegate(closure, resolverBuilder);
        return resolverBuilder;
    }
}
