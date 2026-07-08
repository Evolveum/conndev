/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.yaml.model;

/**
 * The {@code normalize} section of a search operation: {@code toSingleValue} is a plain attribute
 * name; the {@code rewrite*}/{@code restore*} fields carry Groovy source (block scalars) compiled
 * to closures.
 */
public class YamlNormalize {

    public String toSingleValue;
    public String rewriteUid;
    public String rewriteName;
    public String restoreUid;
    public String restoreName;
}
