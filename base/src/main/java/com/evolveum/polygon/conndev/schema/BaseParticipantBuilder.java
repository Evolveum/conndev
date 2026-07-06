/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.schema;

import com.evolveum.polygon.conndev.build.api.AttributeResolverBuilder;
import com.evolveum.polygon.conndev.build.api.ReferenceAttributeBuilder;
import com.evolveum.polygon.conndev.build.api.RelationshipBuilder;
import com.evolveum.polygon.conndev.concepts.GroovyClosures;
import groovy.lang.Closure;
import org.identityconnectors.framework.common.objects.ConnectorObjectReference;

public abstract class BaseParticipantBuilder<B extends RelationshipBuilder.Reference<B,P>, P> implements RelationshipBuilder.Participant<B,P> {

    private final BaseObjectClassDefinitionBuilder objectClass;
    private final B parent;

    private AttributeBuilder attribute;
    private Boolean owner;

    public BaseParticipantBuilder(B relationshipBuilder, BaseObjectClassDefinitionBuilder targetClass) {
        this.parent = relationshipBuilder;
        this.objectClass = targetClass;
    }

    AttributeBuilder attribute() {
        return attribute;
    }

    @Override
    public abstract B attribute(String name);/* {
        if (attribute == null) {
            attribute = new AttributeBuilder(objectClass.attribute(name));
            attribute.delegate.connIdBuilder.setType(ConnectorObjectReference.class);
            attribute.delegate.subtype(parent.name);
        }
        return attribute;
    }*/

    @Override
    public B attribute(String name, Closure<?> closure) {
        return GroovyClosures.callAndReturnDelegate(closure, attribute(name));
    }

    @Override
    public boolean owner() {
        return Boolean.TRUE.equals(this.owner);
    }

    @Override
    public B owner(boolean owner) {
        this.owner = owner;
        return (B) this;
    }

    public String objectClass() {
        return objectClass.name();
    }

    static abstract class AttributeBuilder<B extends ReferenceAttributeBuilder<B,P>,P > implements RelationshipBuilder.Reference<B,P>, ReferenceAttributeBuilder.Delegator<B,P> {

        private final B delegate;

        public AttributeBuilder(B delegate) {
            this.delegate = delegate;
        }

        @Override
        public B delegate() {
            return delegate;
        }

        /*
        @Override
        public B resolver(Closure<?> closure) {
            return delegate.resolver(closure);
        }
        */
    }
}
