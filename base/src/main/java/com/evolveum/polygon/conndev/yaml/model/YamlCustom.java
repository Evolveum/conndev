/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.yaml.model;

import java.util.List;

/**
 * The {@code custom} (fully scripted) search: {@code supportedFilters} carry build-time filter
 * specs, {@code implementation} is the scripted search body (Groovy block scalar).
 */
public class YamlCustom {

    public Boolean emptyFilterSupported;
    public List<YamlSupportedFilter> supportedFilters;
    public String implementation;
}
