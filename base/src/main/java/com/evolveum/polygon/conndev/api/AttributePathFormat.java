/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.api;

/**
 * A format for serializing and deserializing {@link AttributePath} instances to and from
 * a source representation of a given type.
 *
 * <p>Built-in implementations are {@link BasicJsonPathFormat}, {@link JsonPointerFormat},
 * and {@link ScimPath} (String-based), and {@link JavaPathFormat} (a no-op format whose
 * source is the {@link AttributePath} itself).</p>
 *
 * @param <S> the type of the source representation
 */
public interface AttributePathFormat<S> {

    /**
     * Parses a source representation in this format.
     *
     * @param source the source representation
     * @return the parsed path
     * @throws ParsingException if the source is not valid in this format
     */
    AttributePath parse(S source);

    /**
     * Serializes a path to a source representation in this format.
     *
     * @param path the path to serialize
     * @return the source representation
     * @throws AttributePathFormatException if the path contains a component that can not be
     *         represented in this format
     */
    S serialize(AttributePath path);
}
