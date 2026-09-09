/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.yaml.decl;

import com.evolveum.polygon.conndev.concepts.DefinitionValue;
import com.evolveum.polygon.conndev.concepts.SourceLocation;
import com.evolveum.polygon.conndev.yaml.GroovyScriptCompiler;
import com.evolveum.polygon.conndev.yaml.YamlProtocolBlockConsumer;
import groovy.lang.Closure;

import java.util.List;
import java.util.Map;

/**
 * The generic, location-aware YAML binding engine. Given a {@link LocatedDocument} (a location-aware
 * tree) and a builder object, it binds each YAML key onto the {@code @Yaml.*}-annotated method that
 * declares it, attaching the key's {@link SourceLocation} to every {@link DefinitionValue} it builds.
 *
 * <p>This is what makes YAML a real runtime front-end (rather than an inert parse): it drives the
 * same live builders the Groovy DSL does, and it is what lets a value's origin carry a precise
 * {@code source:line:col} instead of {@code SourceLocation.UNKNOWN}.
 */
public final class DeclYamlBinder {

    private final LocatedDocument document;
    private final GroovyScriptCompiler compiler;

    public DeclYamlBinder(LocatedDocument document, GroovyScriptCompiler compiler) {
        this.document = document;
        this.compiler = compiler;
    }

    /**
     * Binds the object-node {@code node} onto {@code target}. Unknown keys fail fast, naming the key
     * and its {@code source:line:col}.
     */
    public void bind(LocatedNode node, Object target) {
        if (node == null || node.kind() != LocatedNode.Kind.OBJECT) {
            return;
        }
        bindEntries(node.entries(), target);
    }

    /**
     * Binds a selected set of entries onto {@code target} — the same rules as {@link #bind} but over an
     * explicit entry list rather than a whole object node. Structural handlers use this to bind a
     * sub-selection (e.g. an endpoint's config minus the keys the handler consumed).
     */
    public void bindEntries(List<LocatedNode.Entry> entries, Object target) {
        Map<String, DeclYamlBinding> bindings = DeclYamlBinding.bindingsFor(target);
        for (LocatedNode.Entry entry : entries) {
            DeclYamlBinding binding = bindings.get(entry.key());
            if (binding == null) {
                // A protocol-specific block (e.g. sql:/scim:) is an unknown key to the generic
                // engine; route it to the object-class consumer if there is one, else fail fast.
                if (target instanceof YamlProtocolBlockConsumer consumer) {
                    consumer.applyProtocolBlock(entry.key(), entry.value().toJacksonNode());
                    continue;
                }
                throw unknownKey(target, entry);
            }
            apply(binding, entry, target);
        }
    }

    public LocatedDocument document() {
        return document;
    }

    /** The {@link SourceLocation} of a key (the position of the property name). */
    public SourceLocation locationOf(LocatedNode.Entry entry) {
        return document.location(entry.keyLine(), entry.keyCol());
    }

    public Closure<?> compileClosure(String source) {
        return compiler.compile(source);
    }

    public Closure<?> compileClosure(String source, String parameterName) {
        return compiler.compile(source, parameterName);
    }

    /**
     * Evaluates a build-time Groovy expression with {@code delegate} as its delegate — used for values
     * that must be resolved at load time (e.g. a search filter {@code spec}) rather than carried as a
     * runtime closure.
     */
    public Object evaluate(String source, Object delegate) {
        return compiler.evaluate(source, delegate);
    }

    private void apply(DeclYamlBinding binding, LocatedNode.Entry entry, Object target) {
        LocatedNode value = entry.value();
        SourceLocation location = document.location(entry.keyLine(), entry.keyCol());

        binding.apply(this, target, value, location);
    }

    private IllegalArgumentException unknownKey(Object target, LocatedNode.Entry entry) {
        return new IllegalArgumentException("Unknown key '" + entry.key() + "' for "
                + target.getClass().getSimpleName() + " at " + document.location(entry.keyLine(), entry.keyCol()));
    }


}
