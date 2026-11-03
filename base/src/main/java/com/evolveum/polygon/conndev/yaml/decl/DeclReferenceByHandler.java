/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 */
package com.evolveum.polygon.conndev.yaml.decl;

import com.evolveum.polygon.conndev.build.api.AttributeBuilder.JsonMapping;

/** A UID reference can be a scalar shorthand or a mapping describing each JSON object. */
public final class DeclReferenceByHandler implements CustomYamlHandler {
    @Override
    public void apply(DeclYamlBinder binder, Object target, LocatedNode value) {
        var mapping = (JsonMapping) target;
        if (value.isValue()) {
            mapping.referenceBy(value.text());
        } else if (value.kind() == LocatedNode.Kind.OBJECT) {
            binder.bind(value, mapping.referenceBy("uid"));
        } else {
            throw new IllegalArgumentException("Expected 'uid' or a reference mapping at " + value.line() + ":" + value.col());
        }
    }
}
