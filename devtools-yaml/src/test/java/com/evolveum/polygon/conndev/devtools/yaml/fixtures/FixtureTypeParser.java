/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.devtools.yaml.fixtures;

import com.evolveum.polygon.conndev.concepts.SourceLocation;
import com.evolveum.polygon.conndev.yaml.decl.DeclYamlValueParser;
import com.evolveum.polygon.conndev.yaml.decl.LocatedNode;

/** A fixture {@code @Yaml.ValueParser} target (its shape is declared in the shape file). */
public class FixtureTypeParser implements DeclYamlValueParser {

    @Override
    public Object coerce(LocatedNode value, SourceLocation location, Class<?> targetType) {
        throw new UnsupportedOperationException("Fixture parser - not meant to run");
    }
}
