/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.concepts;

/**
 * Represents a location within a source file or context.
 * Provides access to the source name, line number, and column position.
 * Supports creating modified location instances with updated line and column values.
 */
public interface SourceLocation {

    /**
     * A predefined source location identifier for in-memory execution.
     * This constant is used to represent code that does not originate from
     * a physical source file, typically serving as a fallback location
     * for runtime-generated or dynamically evaluated content.
     * It carries the name "IN-MEMORY" with line and column set to 0.
     */
    SourceLocation RUNTIME = new Impl("IN-MEMORY",0,0);

    /**
     * An instance representing an unknown or unspecified source location.
     * This constant provides default values for name, line, and column, and always returns itself when creating a new location.
     * It is typically used when actual source position information is not available.
     */
    SourceLocation UNKNOWN = new SourceLocation() {

        @Override
        public String name() {
            return "UNKNOWN";
        }

        @Override
        public int line() {
            return 0;
        }

        @Override
        public int column() {
            return 0;
        }

        @Override
        public SourceLocation location(int line, int column) {
            return this;
        }

        @Override
        public String toString() {
            return "UNKNOWN";
        }
    };
    
    /**
     * Returns the name of the source location.
     *
     * @return The name identifying the source, such as a file name, module name, or internal identifier.
     */
    String name();
    int line();
    int column();


    static SourceLocation unknown() {
        return UNKNOWN;
    }

    static SourceLocation runtime() {
        return RUNTIME;
    }

    static SourceLocation from(String source) {
        return from(source, 0, 0);
    }

    static SourceLocation from(String source, int line, int pos) {
        return new Impl(source, line, pos);
    }

    /**
     * Returns a new  instance with the specified line and column,
     * preserving the source name and other attributes of the origin location.
     *
     * @param line   The new line number.
     * @param column The new column number.
     * @return A new SourceLocation instance with the updated position.
     */
    SourceLocation location(int line, int column);


    /**
     * Captures the current source location by inspecting the stack trace when development mode is enabled.
     * It looks for frames whose file name ends with the framework declarative scripts.
     *
     * Returns UNKNOWN if development mode is disabled or no matching source file is detected.
     *
     * @return a SourceLocation instance containing the source, or UNKNOWN if development mode is disabled or no match is found
     */
    static SourceLocation capture() {
        if (DevelopmentMode.isEnabled()) {
            return forceCapture(1, "groovy");
        }
        return UNKNOWN;
    }

    /**
     * Captures the current source location by inspecting the stack trace when development mode is enabled.
     * Iterates through the call stack to find a frame whose file name ends with any of the specified extensions.
     * Returns UNKNOWN if development mode is disabled or no matching source file is detected.
     *
     * @param sourceExtension the file extensions to match against source file names
     * @return a SourceLocation instance containing the matched file name and line number, or UNKNOWN if no match is found or development mode is disabled
     */
    static SourceLocation capture(String... sourceExtension) {
        if (DevelopmentMode.isEnabled()) {
            return forceCapture(1, sourceExtension);
        }
        return UNKNOWN;
    }

    /**
     * Captures the current source location by inspecting the call stack with a specified offset.
     * Iterates through the stack trace starting after the given offset to find a frame whose file name ends with any of the specified extensions.
     * Returns UNKNOWN if no matching source file is detected.
     *
     * @param offset the number of additional stack frames to skip beyond the immediate caller
     * @param sourceExtension the file extensions to match against source file names
     * @return a SourceLocation instance containing the matched file name and line number, or UNKNOWN if no match is found
     */
    static SourceLocation forceCapture(int offset, String... sourceExtension) {
        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
        for (int i = 2 + offset; i < stackTrace.length; i++) {
            StackTraceElement element = stackTrace[i];
            String fileName = element.getFileName();
            if (fileName != null) {
                for (String ext : sourceExtension) {
                    if (fileName.endsWith(ext)) {
                        return SourceLocation.from(fileName, element.getLineNumber(), 0);
                    }
                }
            }
        }
        return UNKNOWN;
    }

    record Impl(String name, int line, int column) implements SourceLocation {

        @Override
        public String toString() {
            return name + ":" + line + (column > 0 ? ":" + column : "");
        }

        @Override
        public SourceLocation location(int line, int column) {
            return new Impl(name, line, column) ;
        }
    }

}
