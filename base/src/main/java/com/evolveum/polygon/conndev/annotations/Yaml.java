/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.annotations;

import com.evolveum.polygon.conndev.yaml.decl.DeclYamlBinder;
import com.evolveum.polygon.conndev.yaml.decl.DeclYamlValueParser;
import com.evolveum.polygon.conndev.yaml.decl.CustomYamlHandler;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declarative YAML binding markers for builder methods — the YAML counterpart of the
 * {@code @Groovy.*} annotations, modelled the same way (a final container class whose nested
 * annotations are referenced as {@code @Yaml.Key}, {@code @Yaml.Custom}, …).
 *
 * <p>Binding is <em>opt-in</em>: the {@link DeclYamlBinder}
 * scans a builder type for the binding methods and ignores everything else, so there is no "skip"
 * annotation — an unmarked getter or utility method is simply invisible to the YAML front-end. A
 * method is a binding when it carries one of these {@code @Yaml.*} markers, or when its single
 * {@code Closure}-typed parameter is annotated {@code @Script.Runtime} (a block-scalar-to-closure
 * binding; {@code @Script.Initialization} closures are Groovy-DSL conveniences, not YAML bindings).
 *
 * <ul>
 *   <li>{@link Key} — a leaf value: the YAML scalar is coerced and passed to the method. If a
 *       same-name {@code DefinitionValue} overload exists it is preferred, so the value (and its
 *       YAML location) is carried as a {@code DefinitionValue}.</li>
 *   <li>(closure) — a method whose single {@code Closure} parameter carries {@code @Script.Runtime}:
 *       the YAML block scalar is compiled to a Groovy {@code Closure} and passed to the method.</li>
 *   <li>{@link Sub} — a no-arg accessor returning a sub-builder; the value sub-map is bound into it.</li>
 *   <li>{@link ValueParser} — override the auto-inferred scalar coercion with a specialised
 *       {@link DeclYamlValueParser}.</li>
 *   <li>{@link Custom} — a structural shape handled by a named {@link CustomYamlHandler}.</li>
 *   <li>{@link Map} — a map of sub-builders: the annotation sits on the {@code String}-arg factory
 *       method that creates the sub-builders, and {@link Map#value()} is the YAML block key (e.g.
 *       {@code @Yaml.Map("attributes")} on {@code attribute(String)} drives an {@code attributes}
 *       block; each entry is bound onto the sub-builder created by invoking the factory with the
 *       entry's key).</li>
 * </ul>
 */
public final class Yaml {

    private Yaml() {
    }

    /**
     * Marks a method as a leaf value binding. The YAML key defaults to the method name; set
     * {@link #value()} to bind a differently-named key (e.g. {@code @Yaml.Key("multiValued")}).
     *
     * <p>If a same-name {@code DefinitionValue}-typed overload of the method exists it is preferred,
     * so the coerced value (and its YAML source location) is carried as a {@code DefinitionValue};
     * otherwise the plain parameter is used.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    public @interface Key {
        String value() default "";
    }

    /** Marks a no-arg accessor that returns a sub-builder to bind the value sub-map into. */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    public @interface Sub {
    }

    /** Overrides the auto-inferred scalar coercion with the given {@link DeclYamlValueParser} implementation. */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    public @interface ValueParser {
        Class<? extends DeclYamlValueParser> value();
    }

    /** Routes a structural YAML shape to the named {@link CustomYamlHandler}. */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    public @interface Custom {
        Class<? extends CustomYamlHandler> value();
    }

    /**
     * Marks the sub-builder factory method of a map-of-sub-builders binding. The annotation sits on
     * the {@code String}-arg factory method (e.g. {@code attribute(String)}); {@link #value()} is the
     * YAML block key. The YAML value is a mapping of name to definition; each entry is bound onto
     * the sub-builder returned by invoking the annotated factory with the entry's key
     * (e.g. {@code @Yaml.Map("attributes")} on {@code attribute(String)}).
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    public @interface Map {
        String value();
    }
}
