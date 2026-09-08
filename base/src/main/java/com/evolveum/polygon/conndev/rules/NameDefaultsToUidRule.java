/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.rules;

import com.evolveum.polygon.conndev.concepts.MappingAction;
import com.evolveum.polygon.conndev.concepts.MappingRule;
import com.evolveum.polygon.conndev.schema.BaseObjectClassDefinitionBuilder;

/**
 * Ensures every object class that has a {@code __UID__} attribute also has a
 * {@code __NAME__} attribute: when nothing — neither protocol-specific detection nor an
 * explicit {@code connIdAttribute("NAME", …)} mapping — has claimed {@code __NAME__}, it is
 * created as a copy of the {@code __UID__} attribute's protocol mapping and marked
 * {@code derivedFromUid}, so runtime code can recognize the derivation without re-deriving
 * it (see {@link BaseObjectClassDefinitionBuilder#applyDefaultNameFromUid}).
 * <p>
 * Runs as an object-class-level structural rule via
 * {@code BaseSchemaBuilder#applyStructuralRules}, after protocol-specific detection rules
 * (e.g. UID/NAME detection) and before the per-attribute structural rules, so the created
 * attribute gets its ConnId type resolved (forced to {@code String}, like every
 * {@code __NAME__}) and its protocol mapping type-coerced like any other attribute.
 */
public final class NameDefaultsToUidRule implements MappingRule.ObjectClassOnly {

    @Override
    public boolean checkIfApplicable(BaseObjectClassDefinitionBuilder<?, ?, ?, ?, ?, ?> objectClass) {
        return objectClass.needsDefaultNameFromUid();
    }

    @Override
    public MappingAction.ObjectClassOnly<BaseObjectClassDefinitionBuilder<?, ?, ?, ?, ?, ?>> createAction() {
        return BaseObjectClassDefinitionBuilder::applyDefaultNameFromUid;
    }
}
