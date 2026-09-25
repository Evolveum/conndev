/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.devtools.yaml.fixtures;

import com.evolveum.polygon.conndev.annotations.Script;
import com.evolveum.polygon.conndev.annotations.Yaml;

import groovy.lang.Closure;

/** A fixture per-object-class builder (the {@code objectClasses.<name>} value). */
public class FixtureClassBuilder {

    @Yaml.Key
    public FixtureClassBuilder description(String description) {
        return this;
    }

    @Yaml.Key
    public FixtureClassBuilder readOnly(boolean readOnly) {
        return this;
    }

    @Yaml.Sub
    public FixtureOperationsBuilder operations() {
        return new FixtureOperationsBuilder();
    }

    public FixtureClassBuilder normalize(@Script.Runtime Closure normalize) {
        return this;
    }
}
