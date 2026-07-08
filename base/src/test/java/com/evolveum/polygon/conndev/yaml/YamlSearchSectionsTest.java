/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.yaml;

import com.evolveum.polygon.conndev.api.FilterSpecification;
import com.evolveum.polygon.conndev.build.api.AttributeResolverBuilder;
import com.evolveum.polygon.conndev.build.api.NormalizationBuilder;
import com.evolveum.polygon.conndev.build.api.SearchOperationBuilder;
import com.evolveum.polygon.conndev.build.api.SearchScriptBuilder;
import com.evolveum.polygon.conndev.groovy.GroovyContext;
import com.evolveum.polygon.conndev.spi.ObjectSearchOperation;
import com.evolveum.polygon.conndev.yaml.model.YamlAttributeResolver;
import com.evolveum.polygon.conndev.yaml.model.YamlCustom;
import com.evolveum.polygon.conndev.yaml.model.YamlNormalize;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import groovy.lang.Closure;
import org.identityconnectors.framework.common.objects.filter.Filter;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

/**
 * The generic search sections of the operations YAML (ported from the scimrest YAML front-end):
 * {@code normalize}, {@code attributeResolvers} and {@code custom} bind from YAML (block scalars
 * carrying Groovy) and drive the closure-free accessors of {@link SearchOperationBuilder}.
 */
public class YamlSearchSectionsTest {

    private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory());

    private final GroovyScriptCompiler compiler = new GroovyScriptCompiler(new GroovyContext());

    @Test
    public void normalizeSectionDrivesNormalizationBuilder() throws Exception {
        var normalize = YAML.readValue("""
                toSingleValue: roles
                rewriteUid: |
                  return original + ":" + value
                restoreUid: |
                  return original.substring(0, original.indexOf(':'))
                """, YamlNormalize.class);
        var searchBuilder = new RecordingSearchBuilder();

        new YamlNormalizeHandler(compiler).load(searchBuilder, normalize);

        assertEquals(searchBuilder.normalization.toSingleValue, "roles");
        assertNotNull(searchBuilder.normalization.rewriteUid);
        assertNotNull(searchBuilder.normalization.restoreUid);
        assertEquals(searchBuilder.normalization.rewriteName, null);
    }

    @Test
    public void attributeResolversSectionDrivesResolverBuilders() throws Exception {
        List<YamlAttributeResolver> resolvers = YAML.readerForListOf(YamlAttributeResolver.class).readValue("""
                - attribute: members
                  resolutionType: PER_OBJECT
                  implementation: |
                    return null
                """);
        var searchBuilder = new RecordingSearchBuilder();

        new YamlAttributeResolverHandler(compiler).load(searchBuilder, resolvers);

        assertEquals(searchBuilder.resolvers.size(), 1);
        var resolver = searchBuilder.resolvers.get(0);
        assertEquals(resolver.attribute, "members");
        assertEquals(resolver.resolutionType, AttributeResolverBuilder.ResolutionType.PER_OBJECT);
        assertNotNull(resolver.implementation);
    }

    @Test
    public void customSectionEvaluatesFilterSpecAndCompilesImplementation() throws Exception {
        var custom = YAML.readValue("""
                emptyFilterSupported: true
                supportedFilters:
                  - spec: |
                      attribute("id").eq().anySingleValue()
                implementation: |
                  return null
                """, YamlCustom.class);
        var searchBuilder = new RecordingSearchBuilder();

        new YamlCustomSearchHandler(compiler).load(searchBuilder, custom);

        var script = searchBuilder.script;
        assertTrue(script.emptyFilterSupported);
        assertEquals(script.supportedFilters.size(), 1);
        assertNotNull(script.supportedFilters.get(0));
        assertNotNull(script.implementation);
    }

    // ---------------------------------------------------------------------------------------------
    // Recording fakes over the conndev builder interfaces
    // ---------------------------------------------------------------------------------------------

    private static final class RecordingSearchBuilder implements SearchOperationBuilder {
        final RecordingNormalization normalization = new RecordingNormalization();
        final List<RecordingResolver> resolvers = new ArrayList<>();
        final RecordingScript script = new RecordingScript();

        @Override public AttributeResolverBuilder attributeResolver() {
            var resolver = new RecordingResolver();
            resolvers.add(resolver);
            return resolver;
        }

        @Override public NormalizationBuilder normalize() {
            return normalization;
        }

        @Override public SearchScriptBuilder custom() {
            return script;
        }

        @Override public ObjectSearchOperation build() {
            return null;
        }
    }

    private static final class RecordingNormalization implements NormalizationBuilder {
        String toSingleValue;
        Closure<?> rewriteUid;
        Closure<?> rewriteName;
        Closure<?> restoreUid;
        Closure<?> restoreName;

        @Override public NormalizationBuilder toSingleValue(String attribute) {
            this.toSingleValue = attribute;
            return this;
        }

        @Override public NormalizationBuilder rewriteUid(Closure<?> implementation) {
            this.rewriteUid = implementation;
            return this;
        }

        @Override public NormalizationBuilder rewriteName(Closure<?> implementation) {
            this.rewriteName = implementation;
            return this;
        }

        @Override public NormalizationBuilder restoreUid(Closure<?> implementation) {
            this.restoreUid = implementation;
            return this;
        }

        @Override public NormalizationBuilder restoreName(Closure<?> implementation) {
            this.restoreName = implementation;
            return this;
        }
    }

    private static final class RecordingResolver implements AttributeResolverBuilder {
        String attribute;
        ResolutionType resolutionType;
        Closure<?> implementation;

        @Override public AttributeResolverBuilder resolutionType(ResolutionType type) {
            this.resolutionType = type;
            return this;
        }

        @Override public AttributeResolverBuilder search(Closure<Filter> closure) {
            return this;
        }

        @Override public AttributeResolverBuilder implementation(Closure<?> closure) {
            this.implementation = closure;
            return this;
        }

        @Override public AttributeResolverBuilder attribute(String attributeName) {
            this.attribute = attributeName;
            return this;
        }
    }

    private static final class RecordingScript implements SearchScriptBuilder {
        boolean emptyFilterSupported;
        final List<FilterSpecification> supportedFilters = new ArrayList<>();
        Closure<?> implementation;

        @Override public SearchScriptBuilder emptyFilterSupported(boolean emptyFilterSupported) {
            this.emptyFilterSupported = emptyFilterSupported;
            return this;
        }

        @Override public SearchScriptBuilder implementation(Closure<?> implementation) {
            this.implementation = implementation;
            return this;
        }

        @Override public SearchScriptBuilder supportedFilter(FilterSpecification filterSpec) {
            supportedFilters.add(filterSpec);
            return this;
        }

        @Override public FilterSpecification.Attribute attribute(String name) {
            return FilterSpecification.attribute(name);
        }
    }
}
