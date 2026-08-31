/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.logging;

/**
 * Thread-local holder of the operation entry currently being executed.
 *
 * <p>Allows components running inside an operation (HTTP clients, SQL lookups, scripts) to
 * reach the active entry via {@code ConnectorLog.currentOperation()} and attach their events
 * to it.
 */
public final class OperationEntryContext {

    private static final ThreadLocal<OperationEntryState> CURRENT = new ThreadLocal<>();

    private OperationEntryContext() {
    }

    /**
     * Returns the operation entry state currently being executed on this thread.
     *
     * @return the current state, or null
     */
    public static OperationEntryState current() {
        return CURRENT.get();
    }

    /** Registers the given state as the current entry of this thread. */
    static void push(OperationEntryState state) {
        CURRENT.set(state);
    }

    /**
     * Restores the parent entry when the given entry completes, if it is still the current
     * entry of this thread.
     */
    static void restore(OperationEntryState state) {
        if (CURRENT.get() == state) {
            if (state.parent() != null) {
                CURRENT.set(state.parent());
            } else {
                CURRENT.remove();
            }
        }
    }

    /**
     * Removes the current entry state (defensive cleanup, e.g. between requests).
     */
    public static void unset() {
        CURRENT.remove();
    }
}
