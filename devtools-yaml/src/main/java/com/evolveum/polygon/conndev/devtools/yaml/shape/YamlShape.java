/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.devtools.yaml.shape;

/**
 * The YAML shape of a single document value, as derived from the {@code @Yaml.*}-annotated builder
 * methods (by the scanner) or from the shape file's declarative DSL (for the opaque parts the
 * scan cannot see: {@code @Yaml.Custom} handlers, {@code @Yaml.ValueParser} coercers and the
 * loader-handled top-level entries).
 *
 * <p>Instances are immutable and classloader-free: type references are carried as fully-qualified
 * name strings, so a shape can outlive (and cross the boundary of) the classloader it was scanned
 * from. {@link DelegatedObjectShape} is the only kind that still carries unresolved type names —
 * it is replaced by a fully merged {@link ObjectShape} during {@code SchemaGeneration} before any
 * generator sees the model.
 */
public sealed interface YamlShape
        permits ObjectShape, DelegatedObjectShape, ListShape, MapShape, UnionShape, LeafShape, ScriptShape, PathShape, AnyShape {

    /** The human-readable origin of this shape (e.g. the builder type or the shape-file section). */
    String source();

    /** A description (Javadoc or shape-file text) for the generated artifacts, or {@code null}. */
    String description();

    /** Whether this shape comes from a deprecated binding. */
    boolean deprecated();
}
