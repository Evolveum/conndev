/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.yaml.decl;

import com.evolveum.polygon.conndev.concepts.SourceLocation;

/**
 * Converts a scalar {@link LocatedNode} into the Java type a builder method expects. The default
 * implementation ({@link DeclDefaultValueParser}) covers the primitive wrappers, numeric types, enums and
 * raw text; connectors supply specialised {@code Coercer}s (via {@code @Yaml.ValueParser}) for semantic
 * types such as ConnId value types, {@code AttributePath} or {@code FilterSpecification}.
 *
 * <p>Implementations throw an {@link IllegalArgumentException} naming the offending value and its
 * {@code SourceLocation} when the value cannot be coerced.
 */
public interface DeclYamlValueParser {

    /**
     * @param value      the scalar node to convert
     * @param location   the location of the YAML key the value belongs to (for error reporting)
     * @param targetType the parameter type the value is being bound to
     * @return the coerced value
     */
    Object coerce(LocatedNode value, SourceLocation location, Class<?> targetType);
}
