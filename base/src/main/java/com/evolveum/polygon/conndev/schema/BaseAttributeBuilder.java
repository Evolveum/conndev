/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.schema;

import com.evolveum.polygon.conndev.build.api.AttributeBuilder;
import com.evolveum.polygon.conndev.build.api.AttributeResolverBuilder;
import com.evolveum.polygon.conndev.build.api.ReferenceAttributeBuilder;
import com.evolveum.polygon.conndev.concepts.Deferred;
import com.evolveum.polygon.conndev.concepts.DefinitionValue;
import com.evolveum.polygon.conndev.concepts.GroovyClosures;
import com.evolveum.polygon.conndev.concepts.SourceLocation;
import com.evolveum.polygon.conndev.groovy.ScriptedSingleAttributeResolverBuilder;
import groovy.lang.Closure;
import org.identityconnectors.framework.common.objects.AttributeInfo;
import org.identityconnectors.framework.common.objects.Name;
import org.identityconnectors.framework.common.objects.Uid;

public class BaseAttributeBuilder<B extends BaseAttributeBuilder<B, A, R, P>,
        A extends AttributeBuilder<? super R, P>,
        R extends ReferenceAttributeBuilder<R, A, P>,
        P extends BaseAttributeDefinition> extends AbstractAttributeBuilder<B ,R, P> implements ReferenceAttributeBuilder<R, A,  P> {

    public Deferred.Settable<BaseAttributeDefinition> deffered = Deferred.settable();
    private DefinitionValue<String> referencedObjectClass = DefinitionValue.emptyDefault();
    private boolean isReference = false;
    ScriptedSingleAttributeResolverBuilder resolverBuilder;

    public BaseAttributeBuilder(BaseObjectClassDefinitionBuilder restObjectClassBuilder, DefinitionValue<String> name) {
        super(restObjectClassBuilder, name);
    }

    @Override
    public R objectClass(String objectClass) {
        var definition = DefinitionValue.from(objectClass, SourceLocation.capture());
        isReference = true;
        this.referencedObjectClass = this.referencedObjectClass.moreSpecific(definition);
        this.connId().referencedObjectClassName(definition);
        return self();
    }

    @Override
    public R subtype(String subtype) {
        connId().subtype(DefinitionValue.from(subtype, SourceLocation.capture()));
        return self();
    }

    @Override
    public R role(String role) {
        connId().roleInReference(DefinitionValue.from(role, SourceLocation.capture()));
        this.isReference = true;
        return self();
    }

    @Override
    public R role(AttributeInfo.RoleInReference role) {
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
        return (P) new BaseAttributeDefinition(this);
    }

    public AttributeResolverBuilder resolver(Closure<?> closure) {
        this.emulated = DefinitionValue.detected(true);
        this.resolverBuilder = new ScriptedSingleAttributeResolverBuilder(objectClass.name(), deffered);
        GroovyClosures.callAndReturnDelegate(closure, resolverBuilder);
        return resolverBuilder;
    }
}
