/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.devtools.yaml.fixtures;

import com.evolveum.polygon.conndev.annotations.Yaml;
import com.evolveum.polygon.conndev.yaml.decl.CustomYamlHandler;

/** A fixture operations builder (with a custom handler whose shape comes from the shape file). */
public class FixtureOperationsBuilder {

    @Yaml.Key
    public FixtureOperationsBuilder enabled(boolean enabled) {
        return this;
    }

    @Yaml.Custom(FixtureHandler.class)
    public FixtureOperationsBuilder endpoint() {
        return this;
    }
}
