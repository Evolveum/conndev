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
    public static boolean isEnabled() {
        return DEVELOPMENT_MODE.get();
    }

    /**
     * Removes the development mode state for the current thread,
     * reverting to the default value (false).
     */
    public static void unset() {
        DEVELOPMENT_MODE.remove();
    }

    public static void enable() {
        DEVELOPMENT_MODE.set(true);
    }

    /**
     * Executes the specified runnable with development mode temporarily set to the given value for the current thread.
     * The current development mode state is preserved and automatically restored after the runnable completes.
     *
     * @param developmentMode the development mode state to apply during the execution of the runnable
     * @param runnable the callable operation to execute
     * @return the result produced by the runnable
     * @throws E if the runnable throws a exception
     */
    public static <V,E extends Throwable> V run(boolean developmentMode, CheckedCallable<V,E> runnable) throws E {
        var previous = isEnabled();
        set(developmentMode);
        try {
            return runnable.call();
        } finally {
            set(previous);
        }
    }
}


