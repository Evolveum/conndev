/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.api;

import com.evolveum.polygon.conndev.concepts.Deferred;
import com.evolveum.polygon.conndev.concepts.DefinitionValue;
import com.evolveum.polygon.conndev.concepts.SourceLocation;

import java.util.Objects;

/**
 * A declared {@link AttributePath} in a given {@link AttributePathFormat}.
 *
 * <p>The declaration keeps the user-provided source ({@link #value()}) together with the
 * format it is written in ({@link #type()}) and their origins and source locations, so
 * that validation errors and error reports can reference exactly what was declared and
 * where. The parsed {@link #actual()} value is computed lazily on first access and cached.</p>
 *
 * <p>For string-based formats ({@link BasicJsonPathFormat}, {@link JsonPointerFormat},
 * {@link ScimPath}) the value is the user-provided path expression. For programmatically
 * built paths the {@link JavaPathFormat} no-op format is used, whose value is the
 * {@link AttributePath} itself.</p>
 *
 * @param <T> the path format
 * @param <V> the raw source type of the format (e.g. {@link String} or {@link AttributePath})
 */
public final class AttributePathDeclaration<T extends AttributePathFormat<V>, V> {

    /** The path format with its origin and location metadata. */
    private final DefinitionValue<T> type;

    /** The user-provided path source with its origin and location metadata. */
    private final DefinitionValue<V> value;

    /** Lazily computed resolved path (computed once; a failed parse is retried on the next access). */
    private final Deferred<AttributePath> actual;

    private AttributePathDeclaration(DefinitionValue<T> type, DefinitionValue<V> value) {
        this.type = type;
        this.value = value;
        this.actual = Deferred.searchable(() -> parseValue(type, value));
    }

    /**
     * Creates a declaration from definition values.
     *
     * @param type the path format with its origin and location metadata
     * @param value the path source with its origin and location metadata
     * @return the declaration (the source is not parsed until {@link #actual()})
     */
    public static <T extends AttributePathFormat<V>, V> AttributePathDeclaration<T, V> of(
            DefinitionValue<T> type,
            DefinitionValue<V> value) {
        Objects.requireNonNull(type, "path format must not be null");
        Objects.requireNonNull(value, "path value must not be null");
        return new AttributePathDeclaration<>(type, value);
    }

    /**
     * Creates a declaration capturing the current source location for both
     * the format and the value.
     *
     * @param type the path format
     * @param value the path source
     * @return the declaration (the source is not parsed until {@link #actual()})
     */
    public static <T extends AttributePathFormat<V>, V> AttributePathDeclaration<T, V> of(T type, V value) {
        Objects.requireNonNull(type, "path format must not be null");
        Objects.requireNonNull(value, "path value must not be null");
        return of(DefinitionValue.from(type, SourceLocation.capture()),
                DefinitionValue.from(value, SourceLocation.capture()));
    }

    /**
     * Returns the path format with its origin and location metadata.
     *
     * @return the format definition value
     */
    public DefinitionValue<T> type() {
        return type;
    }

    /**
     * Returns the user-provided path source with its origin and location metadata.
     *
     * @return the value definition value
     */
    public DefinitionValue<V> value() {
        return value;
    }

    /**
     * Returns the path represented by this declaration.
     *
     * <p>The source is parsed by the declared format on first access and the result
     * cached.</p>
     *
     * @return the resolved {@link AttributePath}
     * @throws ParsingException if the value is not valid in the declared format;
     *         the exception carries the user-provided value and its source location as context
     * @throws IllegalStateException if the declaration has no format or value
     */
    public AttributePath actual() {
        return actual.get();
    }

    private static <T extends AttributePathFormat<V>, V> AttributePath parseValue(
            DefinitionValue<T> type, DefinitionValue<V> value) {
        if (type.isEmpty() || value.isEmpty()) {
            throw new IllegalStateException("Path declaration has no format or value");
        }
        try {
            return type.value().parse(value.value());
        } catch (ParsingException e) {
            e.withContext("path declaration '" + value.value() + "' at " + value.location());
            throw e;
        }
    }
}
