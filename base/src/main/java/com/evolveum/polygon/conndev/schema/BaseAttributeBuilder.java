/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.schema;

import com.evolveum.polygon.conndev.annotations.Script;
import com.evolveum.polygon.conndev.build.api.AttributeBuilder;
import com.evolveum.polygon.conndev.build.api.AttributeResolverBuilder;
import com.evolveum.polygon.conndev.build.api.ReferenceAttributeBuilder;
import com.evolveum.polygon.conndev.concepts.*;
import com.evolveum.polygon.conndev.groovy.ScriptedSingleAttributeResolverBuilder;
import com.evolveum.polygon.conndev.rules.AttributeTypeResolutionRule;
import com.evolveum.polygon.conndev.rules.ComplexTypeImpliesEmbeddedReferenceRule;
import groovy.lang.Closure;
import org.identityconnectors.framework.common.objects.AttributeInfo;

import java.util.List;

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
     * Structural, protocol-neutral rules applied to every attribute right before it freezes —
     * see {@link #build()}. Order matters: {@link ComplexTypeImpliesEmbeddedReferenceRule} must
     * run before {@link AttributeTypeResolutionRule}, since the latter's
     * {@code COMPLEX_TYPE_IS_EMBEDDED_OBJECT} candidate only reads {@code complexType} directly
     * (set independently, immediately, by the {@code complexType()} setter) — but this order
     * still documents the actual dependency between the two rules' effects.
     */
    private static final List<StructuralMappingRule> STRUCTURAL_RULES = List.of(
            new ComplexTypeImpliesEmbeddedReferenceRule(),
            new AttributeTypeResolutionRule());

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
     * Builds and returns a {@code P} attribute definition instance with the specified attributes.
     *
     * <p>Applies {@link #STRUCTURAL_RULES} first — including final ConnId type resolution — so
     * this works whether called as part of the owning object class's {@code build()} (where
     * {@code applyRules()} has already run) or standalone (e.g. tests building a single attribute
     * in isolation). Actual construction is delegated to {@link #newDefinition()} — subclasses
     * that need a connector-specific definition type (e.g. {@code SqlAttributeDefinition}) must
     * override that, not this method, so rule dispatch is never accidentally skipped.
     *
     * @return a new attribute definition instance configured with the current settings
     */
    public final P build() {
        // TODO: Consider refactoring to ConnID schema contributor
        for (StructuralMappingRule rule : STRUCTURAL_RULES) {
            if (rule.checkIfApplicable(null, objectClass, this)) {
                var action = rule.createAction(null);
                if (action != null) {
                    action.applyToAttribute(this);
                }
            }
        }
        return newDefinition();
    }

    /**
     * Constructs the attribute definition instance. Called by {@link #build()}, after structural
     * rule dispatch has already run. Override to construct a connector-specific subtype.
     *
     * @return a new attribute definition instance
     */
    @SuppressWarnings("unchecked")
    protected P newDefinition() {
        return (P) new BaseAttributeDefinition(this);
    }

    /**
     * Configures a scripted resolver for this attribute using a Groovy closure.
     * Marks the attribute as emulated (detected from schema).
     *
     * @param closure the Groovy closure defining the resolver logic
     * @return the scripted resolver builder for further configuration
     */
    public AttributeResolverBuilder resolver(@Script.Initialization Closure<?> closure) {
        this.emulated = DefinitionValue.detected(true);
        this.resolverBuilder = new ScriptedSingleAttributeResolverBuilder(objectClass.name(), deffered);
        GroovyClosures.callAndReturnDelegate(closure, resolverBuilder);
        return resolverBuilder;
    }
}
