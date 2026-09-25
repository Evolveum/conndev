/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.devtools.yaml.fixtures;

import com.evolveum.polygon.conndev.yaml.decl.CustomYamlHandler;
import com.evolveum.polygon.conndev.yaml.decl.DeclYamlBinder;
import com.evolveum.polygon.conndev.yaml.decl.LocatedNode;

/** A fixture {@code @Yaml.Custom} handler (its shape is declared in the shape file). */
public class FixtureHandler implements CustomYamlHandler {

    @Override
    public void apply(DeclYamlBinder binder, Object target, LocatedNode value) {
        throw new UnsupportedOperationException("Fixture handler - not meant to run");
    }
}
