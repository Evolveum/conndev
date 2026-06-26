/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.concepts;

import java.util.function.Supplier;

/**
 * Deferred value without blocking
 */
public abstract class Deferred<T> {

    public abstract T get() throws IllegalStateException;

    /**
     * Creates a deferred value that is already completed with the specified value.
     *
     * @param value the value to be immediately available upon retrieval
     * @return a new Deferred instance that resolves immediately to the provided value
     */
    public static <T> Deferred<T> ready(T value) {
        return new Completed<>(value);
    }

    /**
     * Creates a new mutable deferred value that can be assigned exactly once.
     *
     * @param <T> the type of the deferred value
     * @return a new Settable instance for asynchronous or lazy value assignment
     */
    public static <T> Deferred.Settable<T> settable() {
        return new Settable<>();
    }

    /**
     * Creates a deferred value that performs a lookup to retrieve its content on demand.
     *
     * @param lookup the supplier function used to fetch the value when needed
     * @param <T> the type of the deferred value
     * @return a new Deferred instance that resolves lazily using the provided lookup supplier
     */
    public static <T> Deferred<T> searchable(Supplier<T> lookup) {
        return new Searchable<>(lookup::get);
    }

    /**
     * A mutable deferred value that can be assigned exactly once.
     *
     * Provides a concrete implementation where the
     * underlying value is initialized via {@link #set(T)} and retrieved via
     * {@link #get()}. Attempts to retrieve the value before it has been set will
     * result in an {@link IllegalStateException}. Subsequent calls to {@link #set(T)}
     * after the value has been assigned will also throw an {@link IllegalStateException}.
     *
     * @param <T> the type of the deferred value
     * @see Deferred
     */
    public static class Settable<T> extends Deferred<T> {

        T value;

        @Override
        public T get() throws IllegalStateException {
            if (value == null) {
                throw new IllegalStateException("Value has not been set yet.");
            }
            return value;
        }

        public void set(T value) {
            if (this.value != null) {
                throw new IllegalStateException("Value has already set.");
            }
            this.value = value;
        }
    }

    /**
     * Represents a deferred value that resolves lazily through a search operation.
     *
     * This implementation extends Deferred to provide on-demand value retrieval. It wraps a
     * Search implementation that computes the actual value when get is called. The class
     * supports nested resolution by recursively evaluating chained Searchable instances until
     * the underlying value is obtained.
     *
     * @param <T> the type of the value to be searched and retrieved
     * @see Deferred
     * @see Search
     */
    private static class Searchable<T> extends Deferred<T> {

        private Object value;

        Searchable(Search<T> value) {
            this.value = value;
        }

        @SuppressWarnings("unchecked")
        @Override
        public T get() throws IllegalStateException {
            if (value instanceof Searchable) {
                value = ((Searchable<T>) value).get();
            }
            return (T) value;
        }
    }

    /**
     * Represents a deferred value that is immediately available and pre-resolved.
     *
     *
     * @param <T> the type of the deferred value
     * @see Deferred
     */
    private static class Completed<T> extends Deferred<T> {

        T value;
        Completed(T value) {
            this.value = value;
        }

        @Override
        public T get() throws IllegalStateException {
            return value;
        }
    }

    /**
     * Represents a search operation that retrieves a value of type T on demand.
     *
     * @param <T> the type of value to be searched or resolved
     */
    interface Search<T> {

        T find();
    }
}
