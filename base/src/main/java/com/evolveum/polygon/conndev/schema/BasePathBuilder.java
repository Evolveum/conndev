/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.schema;

import com.evolveum.polygon.conndev.api.AttributePathDeclaration;
import com.evolveum.polygon.conndev.api.AttributePathFormat;
import com.evolveum.polygon.conndev.api.BasicJsonPathFormat;
import com.evolveum.polygon.conndev.build.api.AttributeBuilder;
import com.evolveum.polygon.conndev.concepts.DefinitionValue;
import com.evolveum.polygon.conndev.concepts.SourceLocation;

import java.util.Objects;

/**
 * Builder for creating {@link AttributePathDeclaration} instances from string expressions in a
 * {@link AttributePathFormat} within the Groovy-based schema definition DSL.
 *
 * <p>The path format defaults to {@link BasicJsonPathFormat} (basic JSONPath). The expression
 * is not parsed by {@link #build()}; parsing happens lazily when the declaration's
 * {@link AttributePathDeclaration#actual()} is first accessed.</p>
 */
public class BasePathBuilder implements AttributeBuilder.PathBuilder {

    /** The path format with its source location; empty until {@link #type(AttributePathFormat<?>)} is called. */
    private DefinitionValue<AttributePathFormat<String>> type = DefinitionValue.emptyDefault();

    /** The path expression with its source location. */
    private DefinitionValue<String> value = DefinitionValue.emptyDefault();

    @Override
    public AttributeBuilder.PathBuilder type(AttributePathFormat<String> type) {
        this.type = this.type.moreSpecific(DefinitionValue.from(Objects.requireNonNull(type, "path() format must not be null"),
                SourceLocation.capture()));
        return self();
    }

    @Override
    public AttributeBuilder.PathBuilder value(String value) {
        this.value = this.value.moreSpecific(DefinitionValue.from(Objects.requireNonNull(value, "path() value must not be null"),
                SourceLocation.capture()));
        return self();
    }

    /**
     * Assembles the {@link AttributePathDeclaration} without parsing the expression.
     *
     * @return the declaration (parsed lazily on first {@link AttributePathDeclaration#actual()} access)
     * @throws IllegalStateException if no path value has been configured or it is blank
     */
    @Override
    public AttributePathDeclaration<?,?> build() {
        if (value.value() == null || value.value().isBlank()) {
            throw new IllegalStateException("path() configuration requires a 'value'");
        }
        DefinitionValue<AttributePathFormat<String>> declaredType =
                type.isEmpty() ? DefinitionValue.defaultFrom(BasicJsonPathFormat.INSTANCE) : type;
        return AttributePathDeclaration.of(declaredType, value);
    }
}
