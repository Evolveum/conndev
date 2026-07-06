/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.groovy;

import com.evolveum.polygon.conndev.annotations.Script;
import com.evolveum.polygon.conndev.api.ContextLookup;
import com.evolveum.polygon.conndev.build.api.AttributeResolverBuilder;
import com.evolveum.polygon.conndev.concepts.GroovyClosures;
import com.evolveum.polygon.conndev.groovy.api.AttributeResolutionScriptContext;
import com.evolveum.polygon.conndev.groovy.api.ObjectClassScripting;
import com.evolveum.polygon.conndev.groovy.api.ObjectClassScriptingFacade;
import com.evolveum.polygon.conndev.schema.BaseAttributeDefinition;
import com.evolveum.polygon.conndev.schema.BaseObjectClassDefinition;
import com.evolveum.polygon.conndev.spi.AttributeResolver;
import groovy.lang.Closure;
import groovy.lang.DelegatesTo;
import org.identityconnectors.framework.common.objects.ConnectorObject;
import org.identityconnectors.framework.common.objects.ConnectorObjectBuilder;
import org.identityconnectors.framework.common.objects.ConnectorObjectReference;
import org.identityconnectors.framework.common.objects.filter.Filter;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

public class ScriptedAttributeResolverBuilder implements AttributeResolverBuilder {

    private final Set<BaseAttributeDefinition> attributes = new HashSet<>();

    private final BaseObjectClassDefinition objectClass;
    private ResolutionType resolutionType = ResolutionType.PER_OBJECT;
    private Implementation implementation;

    public ScriptedAttributeResolverBuilder(ConnectorContext context, BaseObjectClassDefinition objectClass) {
        this.objectClass = objectClass;
    }

    @Override
    public ScriptedAttributeResolverBuilder attribute(String attributeName) {
        attributes.add(objectClass.attributeFromProtocolName(attributeName));
        return this;
    }

    @Override
    public ScriptedAttributeResolverBuilder resolutionType(ResolutionType type) {
        this.resolutionType = type;
        return this;
    }

    @Override
    public AttributeResolverBuilder search(@DelegatesTo(AttributeResolutionScriptContext.class) @Script.Runtime Closure<Filter> closure) {
        // FIXME: rewrite that implementation will wrap logic already.
        this.implementation = new SearchBased(closure);
        return this;
    }

    @Override
    public ScriptedAttributeResolverBuilder implementation(@DelegatesTo(AttributeResolutionScriptContext.class) @Script.Runtime Closure<?> closure) {
        this.implementation = new ClosureBased(closure);
        return this;
    }

    abstract class Implementation {

        abstract AttributeResolver build();
    }

    private class SearchBased extends Implementation {
        private final Closure<Filter> closure;

        public SearchBased(Closure<Filter> closure) {
            this.closure = closure;
        }

        @Override
        AttributeResolver build() {
            if (attributes.size() == 1) {
                var attribute = attributes.iterator().next();
                if (ConnectorObjectReference.class.equals(attribute.connId().getType())) {
                    var targetObjectClass = attribute.connId().getReferencedObjectClassName();
                    return new GroovySearchBasedReference(attribute, targetObjectClass, closure);
                }
            }
            throw new UnsupportedOperationException("Not supported yet.");
        }
    }

    private class ClosureBased extends Implementation {
        private final Closure<?> closure;

        public ClosureBased(Closure<?> closure) {
            this.closure = closure;
        }

        @Override
        AttributeResolver build() {
            return new GroovySingleResolver(objectClass.name(), Set.copyOf(attributes), closure);
        }
    }


    public AttributeResolver build() {
        if (implementation == null) {
            return null;
        }

        return implementation.build();


    }

    public ResolutionType resolutionType() {
        return resolutionType;
    }

    record GroovySearchBasedReference(BaseAttributeDefinition attribute,
                                      String targetObjectClass,
                                      Closure<Filter> implementation) implements AttributeResolver {

        @Override
        public Set<BaseAttributeDefinition> getSupportedAttributes() {
            return Set.of(attribute);
        }

        @Override
        public void resolveSingle(ContextLookup lookup, ConnectorObjectBuilder builder) {
            var context = lookup.get(ConnectorContext.class);
            var objectClass = context.schema().objectClass(targetObjectClass);
            var scriptContext = new SingleResolverContext(context, objectClass, builder);

            Filter filter = GroovyClosures.copyAndCall(implementation, scriptContext);
            var results = new ArrayList<ConnectorObject>();
            scriptContext.objectClass(targetObjectClass).search(filter, results::add, skipAttributeResolution());
            var attrValues = new ArrayList<ConnectorObjectReference>();
            for (var result : results) {
                attrValues.add(new ConnectorObjectReference(result));
            }
            builder.addAttribute(attribute.attributeOf(attrValues));
        }

        @Override
        public ResolutionType resolutionType() {
            return ResolutionType.PER_OBJECT;
        }
    }

    record GroovySingleResolver(String objectClass,
            Set<BaseAttributeDefinition> supportedAttributes,
    Closure<?> implementation) implements AttributeResolver {


        @Override
        public Set<BaseAttributeDefinition> getSupportedAttributes() {
            return supportedAttributes;
        }

        @Override
        public void resolveSingle(ContextLookup lookup, ConnectorObjectBuilder builder) {
            var context = lookup.get(ConnectorContext.class);
            var scriptContext = new SingleResolverContext(context, context.schema().objectClass(objectClass), builder);
            GroovyClosures.copyAndCall(implementation, scriptContext);
        }

        @Override
        public ResolutionType resolutionType() {
            return ResolutionType.PER_OBJECT;
        }
    }

    private record SingleResolverContext(ConnectorContext context, BaseObjectClassDefinition definition, ConnectorObjectBuilder value) implements AttributeResolutionScriptContext {

        @Override
        public ObjectClassScripting objectClass(String name) {
            return ObjectClassScriptingFacade.from(context, name);
        }
    }

}
