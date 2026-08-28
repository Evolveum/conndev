/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.api;

/**
 * Thrown when an {@link AttributePath} contains a component that can not be
 * represented in the target path format.
 *
 * <p>For example, a {@link AttributePath.SimpleValueFilter} can not be
 * serialized to a JSON Pointer (RFC 6901), which has no concept of filters.</p>
 */
public class AttributePathFormatException extends RuntimeException {

    public AttributePathFormatException(String message) {
        super(message);
    }
}
