/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.logging;

import com.evolveum.polygon.conndev.concepts.DevelopmentMode;
import com.evolveum.polygon.conndev.devtools.log.CallerLocation;

/**
 * Captures the location of the code calling into the logging facade.
 *
 * <p>The stack is walked past the logging package frames; the first frame outside
 * {@code com.evolveum.polygon.conndev.logging} is the call site. Groovy script frames (file
 * name ending in {@code .groovy}) produce a script location, all other frames a Java location.
 *
 * <p>Returns null when development mode is disabled — capture is a development-mode feature
 * with no production overhead.
 */
public final class CallerLocationCapture {

    private static final String[] FACADE_CLASSES = {
            "com.evolveum.polygon.conndev.logging.Slf4jConnectorLog",
            "com.evolveum.polygon.conndev.logging.OperationEntryImpl",
            "com.evolveum.polygon.conndev.logging.ConnectorLog",
            "com.evolveum.polygon.conndev.logging.OperationEntryContext",
            "com.evolveum.polygon.conndev.logging.OperationEntryState",
            "com.evolveum.polygon.conndev.logging.CallerLocationCapture",
    };

    private CallerLocationCapture() {
    }

    /**
     * Captures the caller location of the current thread.
     *
     * @return the caller location, or null when development mode is disabled
     */
    public static CallerLocation capture() {
        if (!DevelopmentMode.isEnabled()) {
            return null;
        }
        var stack = Thread.currentThread().getStackTrace();
        for (int i = 2; i < stack.length; i++) {
            var frame = stack[i];
            var className = frame.getClassName();
            if (className == null || isFacadeOrRuntimeFrame(className)) {
                continue;
            }
            var file = frame.getFileName();
            if (file != null && file.endsWith(".groovy")) {
                return CallerLocation.script(file, frame.getLineNumber());
            }
            return CallerLocation.java(className, frame.getMethodName(), file, frame.getLineNumber());
        }
        return null;
    }

    private static boolean isFacadeOrRuntimeFrame(String className) {
        for (var facadeClass : FACADE_CLASSES) {
            if (className.equals(facadeClass)) {
                return true;
            }
        }
        // the script frame itself carries a plain (unprefixed) class name, so skipping the
        // Groovy internal packages never skips the script location
        return className.startsWith("java.")
                || className.startsWith("jdk.")
                || className.startsWith("org.slf4j.")
                || className.startsWith("org.testng.")
                || className.startsWith("org.codehaus.groovy.")
                || className.startsWith("groovy.");
    }
}
