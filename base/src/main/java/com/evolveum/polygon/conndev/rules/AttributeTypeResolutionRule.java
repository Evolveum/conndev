/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.rules;

import com.evolveum.polygon.conndev.build.ConnIdBuiltInAttribute;
import com.evolveum.polygon.conndev.concepts.DefinitionValue;
import com.evolveum.polygon.conndev.concepts.MappingAction;
import com.evolveum.polygon.conndev.concepts.MappingRule;
import com.evolveum.polygon.conndev.schema.BaseAttributeBuilder;
import org.identityconnectors.framework.common.objects.ConnectorObjectReference;
import org.identityconnectors.framework.common.objects.EmbeddedObject;

import java.util.List;
import java.util.Optional;

/**
 * Decides and sets an attribute's final ConnId type — the one {@link MappingRule.AttributeOnly}
 * rule whose {@link #checkIfApplicable} is always {@code true} (every attribute needs a type) and whose
 * action internally evaluates an ordered priority chain of candidate sources, the first one that
 * has an answer wins. That "first match wins" logic is private to this one rule — it is not a
 * second dispatch strategy the rest of conndev needs to know about; see {@link MappingRule}'s
 * class javadoc.
 * <p>
 * This is the single place that decides the type; nothing else should mutate
 * {@code connIdBuilder}'s type outside of this rule's result. Run via
 * {@code BaseSchemaBuilder#applyStructuralRules}, after resource-level rules (e.g. Uid/Name
 * detection) have already run.
 */
public final class AttributeTypeResolutionRule implements MappingRule.AttributeOnly {

    /** One candidate source in the priority chain — see {@link #STEPS}. */
    private interface Candidate {
        Optional<Class<?>> resolve(BaseAttributeBuilder<?, ?, ?, ?> builder, Class<?> protocolSuggestedType);
    }

    /** Built-in ConnId attributes with a forced type (today: {@code __UID__}/{@code __NAME__}
     * — a hard ConnId framework constraint, see {@code Uid}/{@code Name} javadoc) can only ever
     * be backed by that type — not a heuristic, so it wins over everything. The forced types
     * are declared once, in {@link ConnIdBuiltInAttribute}; this rule is where they apply. */
    private static final Candidate BUILT_IN_FORCED_TYPE = (builder, suggested) -> {
        var builtIn = ConnIdBuiltInAttribute.findBuiltIn(builder.connId().name().value());
        return builtIn != null ? Optional.ofNullable(builtIn.getForcedType()) : Optional.empty();
    };

    /** Reference attributes are always {@link ConnectorObjectReference}-typed. */
    private static final Candidate REFERENCE_IS_CONNECTOR_OBJECT_REFERENCE = (builder, suggested) ->
            builder.isReference() ? Optional.of(ConnectorObjectReference.class) : Optional.empty();

    /** An attribute with a {@code complexType} is always {@link EmbeddedObject}-typed. */
    private static final Candidate COMPLEX_TYPE_IS_EMBEDDED_OBJECT = (builder, suggested) ->
            builder.complexType().isPresent() ? Optional.of(EmbeddedObject.class) : Optional.empty();

    /** Whatever a protocol mapping (JSON/SQL/...) suggested, if anything. */
    private static final Candidate PROTOCOL_MAPPING = (builder, suggested) ->
            Optional.ofNullable(suggested);

    /** Whatever was explicitly declared directly on the ConnId builder — always present at
     * minimum as {@code ConnIdBuilder}'s own {@code String} default, so this candidate never
     * defers further; it is the guaranteed last word. */
    private static final Candidate DECLARED_ON_BUILDER = (builder, suggested) ->
            Optional.ofNullable(builder.connId().type().value());

    private static final List<Candidate> STEPS = List.of(
            BUILT_IN_FORCED_TYPE,
            REFERENCE_IS_CONNECTOR_OBJECT_REFERENCE,
            COMPLEX_TYPE_IS_EMBEDDED_OBJECT,
            PROTOCOL_MAPPING,
            DECLARED_ON_BUILDER);

    @Override
    public boolean checkIfApplicable(BaseAttributeBuilder<?, ?, ?, ?> attribute) {
        return true;
    }

    @Override
    public MappingAction.AttributeOnly<BaseAttributeBuilder<?, ?, ?, ?>> createAction() {
        return target -> {
            var suggested = target.suggestedConnIdType();
            for (var step : STEPS) {
                var result = step.resolve(target, suggested);
                if (result.isPresent()) {
                    target.connId().type(DefinitionValue.detected(result.get()));
                    return;
                }
            }
        };
    }
}
