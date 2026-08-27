/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.concepts;

import com.evolveum.polygon.conndev.build.api.AttributeBuilder;
import com.evolveum.polygon.conndev.build.api.ObjectClassSchemaBuilder;
import com.evolveum.polygon.conndev.groovy.BaseObjectOperationSupportBuilder;

/**
 * Detects a property from protocol-specific discovery metadata and, if applicable, produces a
 * {@link MappingAction} describing its effect — deferred, not applied directly, since a rule
 * evaluated during discovery may need to affect a builder (schema or handler) that doesn't exist
 * yet at evaluation time.
 * <p>
 * A protocol binds all four type parameters once (e.g. {@code SqlAttributeMappingRule extends
 * MappingRule<SqlAttributeMappingRule.Context, SqlObjectClassSchemaBuilderImpl,
 * SqlAttributeBuilderImpl, SqlObjectOperationBuilderImpl>}), so individual rule implementations
 * never write out the generic parameters themselves.
 *
 * @param <C>  the discovery metadata type this rule is evaluated against
 * @param <OC> the object-class builder type
 * @param <A>  the attribute builder type
 * @param <H>  the handler builder type
 */
public interface MappingRule<C, OC extends ObjectClassSchemaBuilder, A extends AttributeBuilder, H extends BaseObjectOperationSupportBuilder> {

    /**
     * Check if this rule has anything to do for the given metadata. Most rules decide purely
     * from {@code context} and never look at {@code objectClass}/{@code attribute}; both are
     * passed anyway so a rule that genuinely needs to inspect the current builder state (as
     * opposed to discovery metadata) can, without a separate rule shape. Whichever of the two
     * doesn't apply to this rule's level (e.g. {@code attribute} for a resource-level rule) is
     * {@code null}.
     *
     * @param context     the discovery metadata
     * @param objectClass the object-class builder, or {@code null} if not applicable at this level
     * @param attribute   the attribute builder, or {@code null} if not applicable at this level
     * @return {@code true} if {@link #createAction} should be called
     */
    boolean checkIfApplicable(C context, OC objectClass, A attribute);

    /**
     * Create the action describing this rule's effect. Called only when
     * {@link #checkIfApplicable} returns {@code true}.
     *
     * @param context the discovery metadata
     * @return the action to apply, or {@code null} if there's nothing to apply after all
     */
    MappingAction<OC, A, H> createAction(C context);
}
