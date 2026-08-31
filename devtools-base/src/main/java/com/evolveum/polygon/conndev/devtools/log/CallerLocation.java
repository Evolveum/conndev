/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.devtools.log;

/**
 * Location of the code that emitted a log event.
 *
 * <p>For Java call sites the class name, method name, file name and line are carried; for
 * Groovy script call sites only the script file name and line are carried (class name and
 * method name are null).
 *
 * @param className  fully qualified class name of the call site (null for scripts)
 * @param methodName method name of the call site (null for scripts)
 * @param file       file name of the call site
 * @param line       line number of the call site
 */
public record CallerLocation(String className, String methodName, String file, int line) {

    /**
     * Creates a caller location for a Java call site.
     *
     * @param className  fully qualified class name
     * @param methodName method name
     * @param file       file name
     * @param line       line number
     * @return the caller location
     */
    public static CallerLocation java(String className, String methodName, String file, int line) {
        return new CallerLocation(className, methodName, file, line);
    }

    /**
     * Creates a caller location for a Groovy script call site.
     *
     * @param file script file name
     * @param line line number within the script
     * @return the caller location
     */
    public static CallerLocation script(String file, int line) {
        return new CallerLocation(null, null, file, line);
    }

    /**
     * Checks whether the location points into a Groovy script.
     *
     * @return true if the location is a script location
     */
    public boolean isScript() {
        return className == null;
    }

    /**
     * Returns a human-readable rendering: {@code fetch(RestPagingAwareObjectRetriever.java:47)}
     * for Java call sites and {@code User.search.groovy:42} for script call sites.
     *
     * @return the rendered location
     */
    public String describe() {
        if (isScript()) {
            return file + ":" + line;
        }
        var source = file != null && line > 0 ? file + ":" + line : className;
        return methodName + "(" + source + ")";
    }
}
