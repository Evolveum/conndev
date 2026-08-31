/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.rules;

import com.evolveum.polygon.conndev.concepts.DefinitionValue;
import com.evolveum.polygon.conndev.concepts.MappingAction;
import com.evolveum.polygon.conndev.concepts.MappingRule;
import com.evolveum.polygon.conndev.schema.BaseAttributeBuilder;
import com.evolveum.polygon.conndev.spi.EmbeddedObjectJsonMapping;
import org.identityconnectors.framework.common.objects.AttributeInfo;
import org.identityconnectors.framework.common.objects.EmbeddedObject;

/**
 * Once an attribute's {@code complexType} (referenced embedded object class) is set, it is
 * always a subject reference to that object class, with a JSON mapping that knows how to
 * read/write the embedded object. The ConnId type itself ({@link EmbeddedObject}) is decided by
 * {@link AttributeTypeResolutionRule}, not here — this rule only owns the reference-relationship
 * and protocol-mapping side effects, which have no ordering conflict with type resolution.
 */
public final class ComplexTypeImpliesEmbeddedReferenceRule implements MappingRule.AttributeOnly {

    @Override
    public boolean checkIfApplicable(BaseAttributeBuilder<?, ?, ?, ?> attribute) {
        return attribute.complexType().isPresent();
    }

    @Override
    public MappingAction.AttributeOnly<BaseAttributeBuilder<?, ?, ?, ?>> createAction() {
        return target -> {
            target.connId().roleInReference(DefinitionValue.detected(AttributeInfo.RoleInReference.SUBJECT.toString()));
            target.connId().referencedObjectClassName(target.complexType());
            target.json().implementation(new EmbeddedObjectJsonMapping(target.contextLookup(), target.complexType().value()));
        };
    }
}
