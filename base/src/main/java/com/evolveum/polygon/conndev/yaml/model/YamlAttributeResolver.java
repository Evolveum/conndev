/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.yaml.model;

/**
 * One entry of the {@code attributeResolvers} list of a search operation: {@code attribute} is a
 * plain name, {@code resolutionType} matches
 * {@link com.evolveum.polygon.conndev.build.AttributeResolverBuilder.ResolutionType}, and
 * {@code implementation} carries Groovy source compiled to a closure.
 */
public class YamlAttributeResolver {

    public String attribute;
    public String resolutionType;
    public String implementation;
}
