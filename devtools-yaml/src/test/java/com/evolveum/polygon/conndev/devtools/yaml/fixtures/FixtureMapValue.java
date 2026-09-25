/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.devtools.yaml.fixtures;

import com.evolveum.polygon.conndev.annotations.Yaml;

/** A fixture map-entry value (the {@code attributes.<name>} value of the {@code @Yaml.Map} factory). */
public class FixtureMapValue {

    @Yaml.Key
    public FixtureMapValue name(String name) {
        return this;
    }

    @Yaml.Key
    public FixtureMapValue required(boolean required) {
        return this;
    }
}
