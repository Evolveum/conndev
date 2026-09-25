/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.devtools.yaml.fixtures;

import com.evolveum.polygon.conndev.annotations.Yaml;

/** A fixture subclass — the scan must walk the hierarchy. */
public class FixtureSubclassBuilder extends FixtureClassBuilder {

    @Yaml.Key
    public FixtureSubclassBuilder extension(String extension) {
        return this;
    }
}
