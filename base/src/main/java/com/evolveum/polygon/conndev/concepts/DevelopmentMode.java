/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.concepts;

/**
 * Thread-local flag to indicate whether the code is running in development mode.
 * This allows enabling specific debugging, testing, or development behavior
 * on a per-thread basis without affecting other threads.
 */
public final class DevelopmentMode {

    private static final ThreadLocal<Boolean> DEVELOPMENT_MODE = ThreadLocal.withInitial(() -> false);

    private DevelopmentMode() {
        // Utility class
    }

    /**
     * Enables development mode for the current thread.
     *
     * @param active true to enable, false to disable
     */
    public static void set(boolean active) {
        DEVELOPMENT_MODE.set(active);
    }

    /**
     * Checks if development mode is currently active for the current thread.
     *
     * @return true if active, false otherwise
     */
    public static boolean enabled() {
        return DEVELOPMENT_MODE.get();
    }

    /**
     * Removes the development mode state for the current thread,
     * reverting to the default value (false).
     */
    public static void unset() {
        DEVELOPMENT_MODE.remove();
    }
}


