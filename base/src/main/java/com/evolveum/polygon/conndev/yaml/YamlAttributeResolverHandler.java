/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.yaml;

import com.evolveum.polygon.conndev.build.api.AttributeResolverBuilder;
import com.evolveum.polygon.conndev.build.api.SearchOperationBuilder;
import com.evolveum.polygon.conndev.yaml.model.YamlAttributeResolver;

import java.util.List;

/**
 * Maps the typed {@code attributeResolvers} list. Each resolver is created via the closure-free
 * {@code attributeResolver()} accessor, then filled: {@code attribute} (plain name), optional
 * {@code resolutionType} ({@code PER_OBJECT}/{@code BATCH}) and the {@code implementation} Groovy block.
 */
public final class YamlAttributeResolverHandler {

    private final GroovyScriptCompiler scriptCompiler;

    public YamlAttributeResolverHandler(GroovyScriptCompiler scriptCompiler) {
        this.scriptCompiler = scriptCompiler;
    }

    public void load(SearchOperationBuilder searchBuilder, List<YamlAttributeResolver> resolvers) {
        if (resolvers == null) {
            return;
        }
        for (YamlAttributeResolver resolver : resolvers) {
            AttributeResolverBuilder rb = searchBuilder.attributeResolver();
            if (resolver.attribute != null) {
                rb.attribute(resolver.attribute);
            }
            if (resolver.resolutionType != null) {
                rb.resolutionType(AttributeResolverBuilder.ResolutionType.valueOf(resolver.resolutionType));
            }
            if (resolver.implementation != null) {
                rb.implementation(scriptCompiler.compile(resolver.implementation));
            }
            // TODO: search-based resolver (rb.search(...)).
        }
    }
}
