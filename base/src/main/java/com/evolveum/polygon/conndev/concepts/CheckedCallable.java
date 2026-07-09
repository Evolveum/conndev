/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.concepts;

/**
 * A functional interface representing a computation that may throw a n exception.
 *
 * This interface enables the use of lambda expressions or method references that throw
 * a specific checked exception type, avoiding the need to wrap  exceptions in
 * runtime exceptions or use custom exception-handling wrappers.
 *
 * @param <V> the type of the result produced by the computation
 * @param <E> the type of checked exception that may be thrown by the computation
 */
public interface CheckedCallable<V, E extends Throwable> {

    V call() throws E;

}
