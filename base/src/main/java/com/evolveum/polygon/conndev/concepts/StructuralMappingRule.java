/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.concepts;

import com.evolveum.polygon.conndev.groovy.BaseObjectOperationSupportBuilder;
import com.evolveum.polygon.conndev.schema.BaseAttributeBuilder;
import com.evolveum.polygon.conndev.schema.BaseObjectClassDefinitionBuilder;

/**
 * A thin binding of {@link MappingRule} for protocol-neutral rules that inspect and mutate only
 * an attribute builder's own already-set state — no discovery metadata. {@code C} is
 * {@link Void} (always {@code null}); the rule's {@code checkIfApplicable} reads whatever it
 * needs directly from the {@code attribute} argument. {@code H} is never actually dispatched
 * (structural rules never override {@code applyToHandler}), so it is bound to conndev's own
 * handler builder type purely for type consistency, not because a value ever flows through it.
 */
public interface StructuralMappingRule extends MappingRule<
        Void,
        BaseObjectClassDefinitionBuilder<?, ?, ?, ?, ?, ?>,
        BaseAttributeBuilder<?, ?, ?, ?>,
        BaseObjectOperationSupportBuilder<?, ?, ?, ?, ?>> {
}
