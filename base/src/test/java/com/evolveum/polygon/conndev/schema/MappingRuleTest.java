/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.schema;

import com.evolveum.polygon.conndev.api.ContextLookup;
import com.evolveum.polygon.conndev.build.api.ObjectClassSchemaBuilder;
import com.evolveum.polygon.conndev.concepts.DefinitionValue;
import com.evolveum.polygon.conndev.concepts.MappingAction;
import com.evolveum.polygon.conndev.concepts.MappingRule;
import com.evolveum.polygon.conndev.concepts.SourceLocation;
import com.evolveum.polygon.conndev.groovy.BaseObjectOperationSupportBuilder;
import org.identityconnectors.framework.common.objects.Uid;
import org.identityconnectors.framework.spi.Configuration;
import org.identityconnectors.framework.spi.Connector;
import org.testng.annotations.Test;

import static org.testng.Assert.*;

/**
 * Verifies that {@link MappingRule} binds correctly against a real (if minimal)
 * {@link ObjectClassSchemaBuilder}/{@link com.evolveum.polygon.conndev.build.api.AttributeBuilder}
 * implementation — the same shape a connector (e.g. SQL's
 * {@code SqlObjectClassSchemaBuilderImpl}/{@code SqlAttributeBuilderImpl} or SCIM's
 * {@code RestObjectClassSchemaBuilder}/{@code RestAttributeBuilder}) would bind, both for the
 * metadata-carrying case (discovery-time rules) and the context-free case ({@link Void}, e.g.
 * structural invariants) — and that the dispatch contract (checkIfApplicable, then createAction,
 * then the action's applyToSchema/applyToAttribute) holds at runtime.
 */
public class MappingRuleTest {

    private static final class StubConnector implements Connector {
        @Override
        public Configuration getConfiguration() {
            return null;
        }

        @Override
        public void init(Configuration configuration) {
        }

        @Override
        public void dispose() {
        }
    }

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

        TestObjectClass(BaseSchemaBuilder parent, DefinitionValue<String> name) {
            super(parent, name);
        }

        @Override
        protected TestAttributeBuilder newAttribute(DefinitionValue<String> def) {
            return new TestAttributeBuilder(this, def);
        }
    }

    private static TestObjectClass newObjectClass() {
        var schemaBuilder = new BaseSchemaBuilder(StubConnector.class, ContextLookup.none());
        return new TestObjectClass(schemaBuilder, DefinitionValue.from("Test", SourceLocation.capture()));
    }

    /** A connector fixes the context/builder types via its own narrower rule interface, e.g. this
     * one — mirrors the shape of {@code SqlResourceMappingRule}/{@code ScimResourceMappingRule}. */
    private interface TestMappingRule extends MappingRule<String, TestObjectClass, TestAttributeBuilder, BaseObjectOperationSupportBuilder<?, ?, ?, ?, ?>> {
    }

    /** Mirrors the shape of {@code ScimUidDetectionRule}: detect from context, mutate the schema. */
    private static final class UidDetectionRule implements TestMappingRule {
        @Override
        public boolean checkIfApplicable(String context, TestObjectClass objectClass, TestAttributeBuilder attribute) {
            return "id".equals(context);
        }

        @Override
        public MappingAction<TestObjectClass, TestAttributeBuilder, BaseObjectOperationSupportBuilder<?, ?, ?, ?, ?>> createAction(String context) {
            return new MappingAction<>() {
                @Override
                public void applyToSchema(TestObjectClass objectClass) {
                    var idAttribute = objectClass.attribute("id");
                    if (objectClass.connIdAttributeNotDefined(Uid.NAME)) {
                        idAttribute.connId().name(Uid.NAME);
                    }
                }
            };
        }
    }

    /** Mirrors the shape of {@code ScimUidDetectionRule} being dispatched by a translator. */
    @Test
    public void resourceRuleMapsAttributeToUid() {
        var objectClass = newObjectClass();
        var rule = new UidDetectionRule();

        assertTrue(rule.checkIfApplicable("id", objectClass, null));
        assertFalse(rule.checkIfApplicable("other", objectClass, null));
        assertTrue(objectClass.connIdAttributeNotDefined(Uid.NAME));

        rule.createAction("id").applyToSchema(objectClass);

        assertFalse(objectClass.connIdAttributeNotDefined(Uid.NAME));
    }

    /** Mirrors the shape of {@code ScimNameDetectionRule}: check attribute-level context, mutate
     * only the already-resolved attribute builder — never the object class. */
    private static final class TypeOverrideRule implements TestMappingRule {
        @Override
        public boolean checkIfApplicable(String context, TestObjectClass objectClass, TestAttributeBuilder attribute) {
            return "userName".equals(context);
        }

        @Override
        public MappingAction<TestObjectClass, TestAttributeBuilder, BaseObjectOperationSupportBuilder<?, ?, ?, ?, ?>> createAction(String context) {
            return new MappingAction<>() {
                @Override
                public void applyToAttribute(TestAttributeBuilder attribute) {
                    attribute.connId().type(Integer.class);
                }
            };
        }
    }

    @Test
    public void attributeRuleMutatesOnlyTheResolvedAttribute() {
        var objectClass = newObjectClass();
        var attribute = objectClass.attribute("userName");
        assertEquals(attribute.connId().type().value(), String.class);

        var rule = new TypeOverrideRule();
        assertTrue(rule.checkIfApplicable("userName", objectClass, attribute));
        assertFalse(rule.checkIfApplicable("other", objectClass, attribute));

        rule.createAction("userName").applyToAttribute(attribute);

        assertEquals(attribute.connId().type().value(), Integer.class);
    }

    /** Mirrors a structural, context-free rule (see {@code AttributeTypeResolutionRule}): only
     * ever inspects the target's own state (passed as the {@code attribute} parameter of
     * {@code checkIfApplicable}), never any external context. */
    private static final class AlwaysUppercaseNameRule implements MappingRule<Void, TestObjectClass, TestAttributeBuilder, BaseObjectOperationSupportBuilder<?, ?, ?, ?, ?>> {
        @Override
        public boolean checkIfApplicable(Void context, TestObjectClass objectClass, TestAttributeBuilder attribute) {
            return true;
        }

        @Override
        public MappingAction<TestObjectClass, TestAttributeBuilder, BaseObjectOperationSupportBuilder<?, ?, ?, ?, ?>> createAction(Void context) {
            return new MappingAction<>() {
                @Override
                public void applyToAttribute(TestAttributeBuilder target) {
                    target.connId().type(String.class);
                }
            };
        }
    }

    @Test
    public void contextFreeRuleIgnoresContextAndInspectsOnlyTheTarget() {
        var objectClass = newObjectClass();
        var attribute = objectClass.attribute("displayName");

        var rule = new AlwaysUppercaseNameRule();
        assertTrue(rule.checkIfApplicable(null, objectClass, attribute));

        rule.createAction(null).applyToAttribute(attribute);

        assertEquals(attribute.connId().type().value(), String.class);
    }
}
