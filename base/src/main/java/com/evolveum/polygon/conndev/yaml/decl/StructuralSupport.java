/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.yaml.decl;

import com.evolveum.polygon.conndev.build.api.ObjectClassSchemaBuilder;

import java.util.List;

/**
 * Small helpers shared by the object-class structural handlers: an object-class builder cast and a
 * "this value must be a mapping" check (an empty/omitted block binds nothing).
 */
final class StructuralSupport {

    private StructuralSupport() {
    }

    static ObjectClassSchemaBuilder<?, ?, ?> objectClass(Object target) {
        if (target instanceof ObjectClassSchemaBuilder<?, ?, ?> objectClass) {
            return objectClass;
        }
        throw new IllegalStateException("Expected an object class builder, got "
                + (target == null ? "null" : target.getClass().getSimpleName()));
    }

    static List<LocatedNode.Entry> mapEntries(LocatedNode value, String key) {
        if (value == null || value.isNull()) {
            return List.of();
        }
        if (value.kind() != LocatedNode.Kind.OBJECT) {
            throw new IllegalArgumentException("Expected a mapping for the '" + key + "' block but found a "
                    + value.kind() + " at " + value.line() + ":" + value.col());
        }
        return value.entries();
    }
}
