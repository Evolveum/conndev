/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.api;

import com.evolveum.polygon.conndev.concepts.DefinitionValue;
import com.evolveum.polygon.conndev.concepts.SourceLocation;
import com.evolveum.polygon.conndev.schema.BaseAttributeBuilder;
import com.evolveum.polygon.conndev.schema.BaseAttributeDefinition;
import com.evolveum.polygon.conndev.schema.BaseObjectClassDefinition;
import com.evolveum.polygon.conndev.schema.BaseObjectClassDefinitionBuilder;
import com.evolveum.polygon.conndev.schema.BaseSchemaBuilder;
import com.evolveum.polygon.conndev.schema.StubConnector;
import org.identityconnectors.framework.common.objects.Attribute;
import org.identityconnectors.framework.common.objects.AttributeBuilder;
import org.identityconnectors.framework.common.objects.AttributeDelta;
import org.identityconnectors.framework.common.objects.AttributeDeltaBuilder;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.testng.annotations.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies {@link AttributeSupport#isSupported} against the ICF value shapes that actually reach
 * it. ConnId's {@link AttributeDeltaBuilder} leaves any of the add/remove/replace lists {@code null}
 * when that kind of modification is absent (e.g. {@code build(name, add, remove)} never sets
 * replace), so the value-constrained support check must treat a null list as "no modification of
 * that kind" instead of failing on it.
 */
public class AttributeSupportTest {

    /** Minimal attribute builder: B, A and R collapsed into a single self-referential type. */
    private static final class TestAttributeBuilder extends BaseAttributeBuilder<
            TestAttributeBuilder, TestAttributeBuilder, TestAttributeBuilder, BaseAttributeDefinition> {
        TestAttributeBuilder(BaseObjectClassDefinitionBuilder parent, DefinitionValue<String> name) {
            super(parent, name);
        }
    }

    /** Minimal object class builder pairing with {@link TestAttributeBuilder}. */
    private static final class TestObjectClass extends BaseObjectClassDefinitionBuilder<
            TestObjectClass,
            BaseObjectClassDefinition<BaseAttributeDefinition>,
            TestAttributeBuilder,
            TestAttributeBuilder,
            TestAttributeBuilder,
            BaseAttributeDefinition> {

        TestObjectClass(BaseSchemaBuilder parent, DefinitionValue<ObjectClass> name) {
            super(parent, name);
        }

        @Override
        protected TestAttributeBuilder newAttribute(DefinitionValue<String> def) {
            return new TestAttributeBuilder(this, def);
        }
    }

    private static BaseAttributeDefinition definition(String name) {
        var schemaBuilder = new BaseSchemaBuilder(StubConnector.class, ContextLookup.none());
        var objectClass = new TestObjectClass(schemaBuilder, DefinitionValue.from(new ObjectClass("Test"), SourceLocation.capture()));
        return new BaseAttributeDefinition(objectClass.attribute(name));
    }

    private static AttributeSupport values(String name, Object... supported) {
        var builder = new AttributeSupport.Builder();
        for (var value : supported) {
            builder.value(value);
        }
        return builder.build(definition(name));
    }

    // ==================== unconstrained ====================

    @Test
    public void unconstrainedSupportsEverything() {
        var support = new AttributeSupport.Builder().build(definition("status"));
        assertThat(support.isSupported(AttributeBuilder.build("status", List.of("anything")))).isTrue();
        assertThat(support.isSupported(AttributeDeltaBuilder.build("status", List.of("anything"), null))).isTrue();
        assertThat(support.isSupported(AttributeDeltaBuilder.build("status"))).isTrue();
        assertThat(support.isSupported(AttributeBuilder.build("other", List.of("x")))).isFalse();
        assertThat(support.isSupported(AttributeDeltaBuilder.build("other", List.of("x"), null))).isFalse();
        assertThat(support.isSupported((Attribute) null)).isFalse();
        assertThat(support.isSupported((AttributeDelta) null)).isFalse();
    }

    // ==================== value-constrained: attribute ====================

    @Test
    public void valuesConstrainAttribute() {
        var support = values("status", "active", "disabled");
        assertThat(support.isSupported(AttributeBuilder.build("status", "active"))).isTrue();
        assertThat(support.isSupported(AttributeBuilder.build("status", List.of("active", "disabled")))).isTrue();
        assertThat(support.isSupported(AttributeBuilder.build("status", "locked"))).isFalse();
        assertThat(support.isSupported(AttributeBuilder.build("status", List.of("active", "locked")))).isFalse();
        assertThat(support.isSupported(AttributeBuilder.build("other", "active"))).isFalse();
    }

    @Test
    public void valuesConstrainAttributeWithoutValues() {
        // An attribute carrying no values has nothing to check against
        var support = values("status", "active");
        var empty = new AttributeBuilder().setName("status").build();
        assertThat(support.isSupported(empty)).isTrue();
    }

    // ==================== value-constrained: delta ====================

    @Test
    public void valuesConstrainAddOnlyDelta() {
        // AttributeDeltaBuilder.build(name, add, remove) leaves the replace list null
        var support = values("status", "active", "disabled");
        assertThat(support.isSupported(AttributeDeltaBuilder.build("status", List.of("active"), null))).isTrue();
        assertThat(support.isSupported(AttributeDeltaBuilder.build("status", List.of("locked"), null))).isFalse();
    }

    @Test
    public void valuesConstrainRemoveOnlyDelta() {
        // Removing values never introduces an unsupported value — the add/replace lists are null
        var support = values("status", "active");
        assertThat(support.isSupported(AttributeDeltaBuilder.build("status", null, List.of("anything")))).isTrue();
    }

    @Test
    public void valuesConstrainReplaceOnlyDelta() {
        var support = values("status", "active", "disabled");
        assertThat(support.isSupported(AttributeDeltaBuilder.build("status", List.of("disabled")))).isTrue();
        assertThat(support.isSupported(AttributeDeltaBuilder.build("status", "locked"))).isFalse();
        assertThat(support.isSupported(AttributeDeltaBuilder.build("status", List.of("active", "locked")))).isFalse();
    }

    @Test
    public void valuesConstrainAddAndRemoveDelta() {
        var support = values("status", "active");
        assertThat(support.isSupported(AttributeDeltaBuilder.build("status", List.of("active"), List.of("locked")))).isTrue();
        assertThat(support.isSupported(AttributeDeltaBuilder.build("status", List.of("locked"), List.of("active")))).isFalse();
    }

    @Test
    public void valuesConstrainEmptyDelta() {
        // No modification at all — every list is null
        var support = values("status", "active");
        assertThat(support.isSupported(AttributeDeltaBuilder.build("status"))).isTrue();
        assertThat(support.isSupported(AttributeDeltaBuilder.build("other"))).isFalse();
    }

    // ==================== transition-constrained (not yet implemented) ====================

    @Test
    public void transitionOnlySupportsNothingYet() {
        // FIXME in isSupported: transition checking is not implemented yet, so a
        // transition-only constraint rejects everything for now
        var builder = new AttributeSupport.Builder();
        builder.transition("active", "disabled");
        var support = builder.build(definition("status"));
        assertThat(support.isSupported(AttributeBuilder.build("status", "active"))).isFalse();
        assertThat(support.isSupported(AttributeDeltaBuilder.build("status", List.of("disabled"), null))).isFalse();
    }
}
