/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.rules;

import com.evolveum.polygon.conndev.concepts.MappingAction;
import com.evolveum.polygon.conndev.concepts.MappingRule;
import com.evolveum.polygon.conndev.schema.BaseAttributeBuilder;

/**
 * Aligns an attribute's protocol value mappings with its final ConnId type — the
 * {@link MappingRule.AttributeOnly} rule that runs right after
 * {@link AttributeTypeResolutionRule} (see {@code BaseSchemaBuilder#applyStructuralRules}) and
 * pushes the resolved type into every protocol mapping builder via
 * {@link com.evolveum.polygon.conndev.schema.AttributeProtocolMappingBuilder#applyConnIdTypeOverride(Class)}.
 * <p>
 * This is the single place where the wire↔ConnId value-type conversion decision is made. A
 * protocol mapping whose native ConnId type differs from the final one (e.g. an
 * {@code int64}-backed {@code __UID__} presented to ConnId as {@code String}) is wrapped with a
 * value-type override when the mapping is built; the protocol-side wire representation stays
 * native. Nothing else in the framework decides a mapping's ConnId type.
 */
public final class AttributeTypeCoercionRule implements MappingRule.AttributeOnly {

    @Override
    public boolean checkIfApplicable(BaseAttributeBuilder<?, ?, ?, ?> attribute) {
        return true;
    }

    @Override
    public MappingAction.AttributeOnly<BaseAttributeBuilder<?, ?, ?, ?>> createAction() {
        return attribute -> {
            var finalType = attribute.connId().type().value();
            if (finalType == null) {
                return;
            }
            for (var mappingBuilder : attribute.protocolMappingBuilders()) {
                mappingBuilder.applyConnIdTypeOverride(finalType);
            }
        };
    }
}
