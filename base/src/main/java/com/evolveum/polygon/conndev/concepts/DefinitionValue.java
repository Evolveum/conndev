/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.concepts;

import java.util.Objects;

public record DefinitionValue<T>(T value, Origin origin, SourceLocation location) {


    public static final DefinitionValue<Boolean> DEFAULT_FALSE = defaultFrom(Boolean.FALSE);
    public static final DefinitionValue<Boolean> DEFAULT_TRUE = defaultFrom(Boolean.TRUE);
    public static final DefinitionValue<Void> DEFAULT_NULL = defaultFrom(null);

    public static <T> DefinitionValue<T> of(T value, Origin original, SourceLocation location) {
        return new DefinitionValue<>(value, original, location != null ? location : SourceLocation.UNKNOWN);
    }

    public static <T> DefinitionValue<T> from(T value, SourceLocation location) {
        return of(value, Origin.DECLARED, location);
    }


    @SuppressWarnings("unchecked")
    public static <T> DefinitionValue<T> emptyDefault() {
        return (DefinitionValue<T>) DEFAULT_NULL;
    }

    /**
     * Constructs a DefinitionValue representing a framework default value.
     *
     * @param value the value to wrap
     * @return a new DefinitionValue instance containing the specified value with the DEFAULT origin
     */
    public static <T> DefinitionValue<T> defaultFrom(T value) {
        return new DefinitionValue<>(value, Origin.DEFAULT, SourceLocation.RUNTIME);
    }

    /**
     * Constructs a DefinitionValue representing detected definition (derived from remote system).
     *
     * @param value the value to wrap
     * @return a new DefinitionValue instance containing the specified value with the DETECTED origin
     */
    public static <T> DefinitionValue<T> detected(T value) {
        return new DefinitionValue<>(value, Origin.DETECTED, SourceLocation.RUNTIME);
    }

    /**
     * Compares the origin precedence of this definition value with another and returns the more specific value.
     *
     * If both values have the same origin, an exception is thrown unless the origin is DETECTED, in which case the provided value is returned.
     * When origins differ, the value with the higher precedence origin is selected.
     *
     * @param other the definition value to compare against
     * @return the definition value with higher origin precedence
     * @throws IllegalStateException if both values have the DEFAULT origin
     * @throws IllegalArgumentException if both values have the DECLARED origin at different locations
     */
    public DefinitionValue<T> moreSpecific(DefinitionValue<T> other) {
        if (this.origin == other.origin) {
            if (this.origin == Origin.DEFAULT) {
                throw new IllegalStateException("Multiple default values declared.");
            } else if (this.origin == Origin.DETECTED) {
                return other;
            } else if (!Objects.equals(this.value, other.value)) {
                throw new IllegalArgumentException("Multiple declarations for the same definition detected. Current value " + this + " other: " + other);
            }
        }
        return this.origin.compareTo(other.origin) > 0 ? this : other;
    }

    public boolean isPresent() {
        return value != null;
    }

    public boolean isEmpty() {
        return value == null;
    }

    public DefinitionValue<T> asDefault() {
        return of(value, Origin.DEFAULT, location);
    }

    public <X> DefinitionValue<X> derived(X value) {
        return of(value, origin, location);
    }

    public enum Origin {
        /**
         * Framework defaults, the least specific value
         */
         DEFAULT,
         /**
         * Value detected from remote system / computed, more specific than default, less specific then explicitly declared
         */
        DETECTED,
        /**
         * Explicitly declared by connector developer using builders / declarative format / groovy scripts
         */
        DECLARED

    }

}
