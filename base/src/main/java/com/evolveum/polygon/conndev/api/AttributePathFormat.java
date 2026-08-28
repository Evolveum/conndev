/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.api;

import java.util.function.Function;

/**
 * A format for serializing and deserializing {@link AttributePath} instances to and from
 * their string representations.
 *
 * <p>Built-in implementations are {@link BasicJsonPathFormat}, {@link JsonPointerFormat},
 * and {@link ScimPath}.</p>
 */
public interface AttributePathFormat {


    /**
     * Parses a path string in this format.
     *
     * @param input the path string
     * @return the parsed path
     * @throws ParsingException if the input is not valid in this format
     */
    AttributePath parse(String input);

    /**
     * Serializes a path to a string in this format.
     *
     * @param path the path to serialize
     * @return the path string
     * @throws AttributePathFormatException if the path contains a component that can not be
     *         represented in this format
     */
    String serialize(AttributePath path);
}
