/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.devtools.yaml.fixtures;

import com.evolveum.polygon.conndev.annotations.Script;
import com.evolveum.polygon.conndev.annotations.Yaml;
import com.evolveum.polygon.conndev.api.AttributePathDeclaration;
import com.evolveum.polygon.conndev.yaml.decl.CustomYamlHandler;

import groovy.lang.Closure;

/**
 * A fixture builder exercising every binding kind the runtime binder accepts: {@code @Yaml.Key}
 * leaves (including a renamed key, a deprecated one and an enum), a {@code @Yaml.Sub}, a
 * {@code @Yaml.Map} factory, a {@code @Yaml.Path}, a {@code @Yaml.Custom} handler, a
 * {@code @Yaml.ValueParser} coercer and the {@code @Script.Runtime} closure bindings — plus the
 * shadowing case (a scripted closure whose name is already a YAML key) and the non-binding
 * convenience methods the scan must skip.
 */
public class FixtureAttributeBuilder {

    @Yaml.Key
    public FixtureAttributeBuilder description(String description) {
        return this;
    }

    @Yaml.Key
    public FixtureAttributeBuilder required(boolean required) {
        return this;
    }

    @Yaml.Key("multiValued")
    public FixtureAttributeBuilder multiValuedFlag(boolean multiValued) {
        return this;
    }

    /** A deprecated binding — the runtime binds it, so the scan must keep it. */
    @Deprecated
    @Yaml.Key
    public FixtureAttributeBuilder jsonType(String jsonType) {
        return this;
    }

    @Yaml.Key
    public FixtureAttributeBuilder resolution(FixtureEnum resolution) {
        return this;
    }

    @Yaml.Key
    @Yaml.ValueParser(FixtureTypeParser.class)
    public FixtureAttributeBuilder nativeType(String nativeType) {
        return this;
    }

    @Yaml.Sub
    public FixtureMappingBuilder json() {
        return new FixtureMappingBuilder();
    }

    @Yaml.Map("attributes")
    public FixtureMapValue attribute(String name) {
        return new FixtureMapValue();
    }

    @Yaml.Key("jsonPath")
    @Yaml.Path
    public FixtureAttributeBuilder jsonPath(AttributePathDeclaration<?, ?> path) {
        return this;
    }

    @Yaml.Custom(FixtureHandler.class)
    public FixtureAttributeBuilder endpoint() {
        return this;
    }

    public FixtureAttributeBuilder body(@Script.Runtime Closure body) {
        return this;
    }

    /** The shadowing case: the YAML key {@code connId} is already bound by {@code connIdValue}, so this scripted closure must not bind. */
    @Yaml.Key("connId")
    public FixtureAttributeBuilder connIdValue(String connId) {
        return this;
    }

    public FixtureAttributeBuilder connId(@Script.Runtime Closure connId) {
        return this;
    }

    /** A plain (non-{@code @Script.Runtime}) closure — a Groovy-DSL convenience, not a YAML binding. */
    public FixtureAttributeBuilder connIdBlock(Closure connIdBlock) {
        return this;
    }

    /** An unmarked utility — invisible to the YAML front-end. */
    public FixtureAttributeBuilder helper(String value) {
        return this;
    }
}
