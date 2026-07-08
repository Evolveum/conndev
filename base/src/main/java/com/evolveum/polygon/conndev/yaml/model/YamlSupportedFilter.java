/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.yaml.model;

/**
 * A supported filter declaration: {@code spec} is a build-time Groovy expression evaluated against
 * the owning builder (e.g. {@code attribute("id").eq().anySingleValue()}) producing a
 * {@link com.evolveum.polygon.conndev.api.FilterSpecification}.
 */
public class YamlSupportedFilter {

    public String spec;
}
