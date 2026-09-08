/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.schema;

import com.evolveum.polygon.conndev.concepts.DefinitionValue;

/** Minimal object class builder pairing with {@link TestAttributeBuilder}. */
public final class TestObjectClass extends BaseObjectClassDefinitionBuilder<
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