/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.devtools.yaml.fixtures;

import com.evolveum.polygon.conndev.annotations.Yaml;

/** A fixture sub-builder (the {@code @Yaml.Sub} target). */
public class FixtureMappingBuilder {

    @Yaml.Key
    public FixtureMappingBuilder name(String name) {
        return this;
    }

    @Yaml.Key
    public FixtureMappingBuilder type(String type) {
        return this;
    }
}
