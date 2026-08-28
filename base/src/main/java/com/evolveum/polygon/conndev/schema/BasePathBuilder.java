/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.schema;

import com.evolveum.polygon.conndev.api.AttributePath;
import com.evolveum.polygon.conndev.api.AttributePathFormat;
import com.evolveum.polygon.conndev.build.api.AttributeBuilder;

import java.util.Objects;

/**
 * Builder for creating {@link AttributePath} instances from string expressions in a
 * {@link AttributePathFormat} within the Groovy-based schema definition DSL.
 *
 * <p>The path format defaults to {@link AttributeBuilder.PathBuilder#JSON_PATH}; the path
 * value is required and is parsed at {@link #build()} time.</p>
 */
public class BasePathBuilder implements AttributeBuilder.PathBuilder {

    /** The format of the path expression. */
    private AttributePathFormat type = JSON_PATH;

    /** The path expression. */
    private String value;

    @Override
    public AttributeBuilder.PathBuilder type(AttributePathFormat type) {
        this.type = Objects.requireNonNull(type, "path() format must not be null");
        return self();
    }

    @Override
    public AttributeBuilder.PathBuilder value(String value) {
        this.value = value;
        return self();
    }

    /**
     * Parses the configured value in the configured format.
     *
     * @return the parsed {@link AttributePath}
     * @throws IllegalStateException if no path value has been configured
     * @throws ParsingException if the value is not valid in the configured format
     */
    @Override
    public AttributePath build() {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("path() configuration requires a 'value'");
        }
        return type.parse(value);
    }
}
