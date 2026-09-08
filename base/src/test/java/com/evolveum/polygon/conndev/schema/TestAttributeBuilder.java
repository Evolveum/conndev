/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.schema;

import com.evolveum.polygon.conndev.concepts.DefinitionValue;

/** Minimal attribute builder: B, A and R collapsed into a single self-referential type. */
public final class TestAttributeBuilder extends BaseAttributeBuilder<
        TestAttributeBuilder, TestAttributeBuilder, TestAttributeBuilder, BaseAttributeDefinition> {
    TestAttributeBuilder(BaseObjectClassDefinitionBuilder parent, DefinitionValue<String> name) {
        super(parent, name);
    }
}
