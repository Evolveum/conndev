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

/**
 * Attribute builder that handles reference attributes in the connector framework.
 * Reference attributes point to objects in other object classes and support
 * ConnID's referencedObjectClassName and roleInReference concepts.
 *
 * @param <B> the self type for fluent interface
 * @param <A> the public attribute builder interface for non-reference attributes
 * @param <R> the public reference attribute builder interface
 * @param <P> the base attribute definition type
 */
public class BaseAttributeBuilder<B extends BaseAttributeBuilder<B, A, R, P>,
        A extends AttributeBuilder<? super R, P>,
        R extends ReferenceAttributeBuilder<R, A, P>,
        P extends BaseAttributeDefinition> extends AbstractAttributeBuilder<B ,R, P> implements ReferenceAttributeBuilder<R, A,  P> {

    /**
     * Deferred setter for the attribute definition, used to delay finalization until all
     * configuration (like resolvers) is complete.
     */
    public Deferred.Settable<BaseAttributeDefinition> deffered = Deferred.settable();

    /**
     * The object class name referenced by this reference attribute.
     */
    private DefinitionValue<String> referencedObjectClass = DefinitionValue.emptyDefault();

    /**
     * Flag indicating whether this attribute is a reference attribute.
     */
    private boolean isReference = false;

    /**
     * Builder for scripted attribute resolver, created when a closure-based resolver is configured.
     */
    ScriptedSingleAttributeResolverBuilder resolverBuilder;

    /**
     * Constructs a new attribute builder with the given name in the specified object class context.
     *
     * @param parent the object class definition builder providing context
     * @param name the name of the attribute
     */
    public BaseAttributeBuilder(BaseObjectClassDefinitionBuilder parent, DefinitionValue<String> name) {
        super(parent, name);
    }

    /**
     * Sets the referenced object class name for this reference attribute.
     * This links the attribute to objects in another object class.
     *
     * @param objectClass the name of the referenced object class
     * @return this builder for method chaining
     */
    @Override
    public R objectClass(String objectClass) {
        var definition = DefinitionValue.from(objectClass, SourceLocation.capture());
        isReference = true;
        this.referencedObjectClass = this.referencedObjectClass.moreSpecific(definition);
        this.connId().referencedObjectClassName(definition);
        return self();
    }

    /**
     * Sets the subtype for this reference attribute.
     *
     * @param subtype the subtype value
     * @return this builder for method chaining
     */
    @Override
    public R subtype(String subtype) {
        connId().subtype(DefinitionValue.from(subtype, SourceLocation.capture()));
        return self();
    }

    /**
     * Sets the role in reference for this reference attribute.
     *
     * @param role the role name
     * @return this builder for method chaining
     */
    @Override
    public R role(String role) {
        connId().roleInReference(DefinitionValue.from(role, SourceLocation.capture()));
        this.isReference = true;
        return self();
    }

    /**
     * Sets the role in reference from a ConnID RoleInReference object.
     *
     * @param role the ConnID role in reference object
     * @return this builder for method chaining
     */
    @Override
    public R role(AttributeInfo.RoleInReference role) {
        return role(role.toString());
    }


    /**
     * Checks whether this attribute is a reference attribute.
     *
     * @return true if this is a reference attribute
     */
    public boolean isReference() {
        return isReference;
    }

    /**
     * Builds and returns a {@code BaseAttributeDefinition} instance with the specified attributes.
     *
     * @return a new {@code BaseAttributeDefinition} instance configured with the current settings
     */
    public P build() {
        // TODO: Consider refactoring to ConnID schema contributor
        return (P) new BaseAttributeDefinition(this);
    }

    /**
     * Configures a scripted resolver for this attribute using a Groovy closure.
     * Marks the attribute as emulated (detected from schema).
     *
     * @param closure the Groovy closure defining the resolver logic
     * @return the scripted resolver builder for further configuration
     */
    public AttributeResolverBuilder resolver(Closure<?> closure) {
        this.emulated = DefinitionValue.detected(true);
        this.resolverBuilder = new ScriptedSingleAttributeResolverBuilder(objectClass.name(), deffered);
        GroovyClosures.callAndReturnDelegate(closure, resolverBuilder);
        return resolverBuilder;
    }
}
