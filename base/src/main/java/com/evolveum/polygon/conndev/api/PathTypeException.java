/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.api;

/**
 * Thrown when a node of the resolved path does not have the type required by the path component.
 *
 * The exception is checked: callers of
 * {@link com.evolveum.polygon.conndev.concepts.Path#resolve(Object, com.evolveum.polygon.conndev.concepts.Path.CheckedResolver)}
 * with a resolver that declares this exception type must handle it explicitly.
 */
public class PathTypeException extends Exception {

    public PathTypeException(String message) {
        super(message);
    }

    public PathTypeException(String message, Throwable cause) {
        super(message, cause);
    }
}
