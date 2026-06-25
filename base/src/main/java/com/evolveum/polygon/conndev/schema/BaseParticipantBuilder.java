/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.schema;

import com.evolveum.polygon.conndev.build.AttributeResolverBuilder;
import com.evolveum.polygon.conndev.build.ReferenceAttributeBuilder;
import com.evolveum.polygon.conndev.build.RelationshipBuilder;
import com.evolveum.polygon.conndev.concepts.GroovyClosures;
import groovy.lang.Closure;
import org.identityconnectors.framework.common.objects.ConnectorObjectReference;

public class BaseParticipantBuilder implements RelationshipBuilder.Participant {

    private final BaseObjectClassDefinitionBuilder objectClass;
    private final AbstractRelationshipBuilder parent;

    private AttributeBuilder attribute;
    private Boolean owner;

    public BaseParticipantBuilder(AbstractRelationshipBuilder relationshipBuilder, BaseObjectClassDefinitionBuilder targetClass) {
        this.parent = relationshipBuilder;
        this.objectClass = targetClass;
    }

    AttributeBuilder attribute() {
        return attribute;
    }

    @Override
    public AttributeBuilder attribute(String name) {
        if (attribute == null) {
            attribute = new AttributeBuilder(objectClass.attribute(name));
            attribute.delegate.connIdBuilder.setType(ConnectorObjectReference.class);
            attribute.delegate.subtype(parent.name);
        }
        return attribute;
    }

    @Override
    public RelationshipBuilder.Reference attribute(String name, Closure<?> closure) {
        return GroovyClosures.callAndReturnDelegate(closure, attribute(name));
    }

    @Override
    public boolean owner() {
        return Boolean.TRUE.equals(this.owner);
    }

    @Override
    public RelationshipBuilder.Participant owner(boolean owner) {
        this.owner = owner;
        return this;
    }

    public String objectClass() {
        return objectClass.name();
    }

    static class AttributeBuilder implements RelationshipBuilder.Reference, ReferenceAttributeBuilder.Delegator {

        private final BaseAttributeBuilder delegate;

        public AttributeBuilder(BaseAttributeBuilder delegate) {
            this.delegate = delegate;
        }

        @Override
        public ReferenceAttributeBuilder delegate() {
            return delegate;
        }

        @Override
        public AttributeResolverBuilder resolver(Closure<?> closure) {
            return delegate.resolver(closure);
        }
    }
}
