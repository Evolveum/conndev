/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.yaml.decl;

import com.evolveum.polygon.conndev.build.api.ObjectClassSchemaBuilder;

/**
 * Binds the class-level {@code connId} alias map ({@code connId: {UID: id, NAME: login}}): each
 * entry maps a ConnId built-in name (UID/NAME) to a protocol attribute already declared under
 * {@code attributes}, and is applied via {@link ObjectClassSchemaBuilder#connIdAttribute(String, String)}.
 * Because the target attribute must exist, this is applied after the {@code attributes} block.
 */
public final class DeclConnIdAliasHandler implements CustomYamlHandler {

    @Override
    public void apply(DeclYamlBinder binder, Object target, LocatedNode value) {
        var objectClass = StructuralSupport.objectClass(target);
        for (var entry : StructuralSupport.mapEntries(value, "connId")) {
            if (entry.value().isNull()) {
                throw new IllegalArgumentException("Class-level connId alias '" + entry.key()
                        + "' is missing the target attribute name");
            }
            objectClass.connIdAttribute(entry.key(), entry.value().text());
        }
    }
}
