/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.groovy;

import groovy.lang.MissingMethodException;

import java.io.Serial;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Sanitizes exceptions thrown while executing connector Groovy code so that runtime values —
 * e.g. credentials passed as method arguments — do not leak into error messages surfaced to
 * the user (connector wizard UI, operation failures, structured logs).
 *
 * <p>Groovy built-in exceptions embed the values of their operands in their messages (e.g.
 * {@code MissingMethodException}: <i>No signature of method: … values: [username, password]</i>).
 * {@link #sanitize(Throwable)} returns a copy of the throwable in which those values are replaced
 * with {@link #REDACTED}.
 *
 * <p>The whole cause chain is sanitized, so {@code Caused by:} sections cannot carry raw values
 * either. Stack traces are preserved, so script line/column reporting (see
 * {@link GroovyScriptValidator}) keeps working.
 *
 * <p>Out of scope: messages written by the connector author (they own the text),
 * compile-phase errors (which echo the user's own script source, not runtime data), and JDK
 * exceptions raised by user code.
 */
public final class GroovyExceptionSanitizer {

    /** Placeholder substituted for values redacted from exception messages. */
    public static final String REDACTED = "****";

    /** Safety bound for cause-chain walking, guards against cyclic {@code initCause} links. */
    private static final int MAX_CAUSE_DEPTH = 32;

    /** Marker separating the argument values from the rest of a {@link MissingMethodException} message. */
    private static final String VALUES_MARKER = " values: ";

    /**
     * Message patterns of Groovy built-in exceptions that embed runtime values of the
     * operands/arguments they describe. Each replacement keeps the class names (useful for
     * debugging) and substitutes the values with {@link #REDACTED}.
     */
    private static final List<LeakPattern> LEAKING_PATTERNS = List.of(
            // GroovyCastException: "Cannot cast object '<value>' with class '<cls>' to class '<t>'"
            new LeakPattern(Pattern.compile("Cannot cast object '(.*)' with class"),
                    "Cannot cast object '" + REDACTED + "' with class"),
            // GroovyCastException: " due to: <cause class>: <cause message>" — the embedded cause
            // message may itself carry values, keep only the cause class
            new LeakPattern(Pattern.compile(" due to: ([A-Za-z0-9_$.]+): .*$"), " due to: $1"),
            // NumberMath: "Cannot use <op> on this number type: <cls> with value: <value>"
            new LeakPattern(Pattern.compile(" with value: .*$"), " with value: " + REDACTED),
            // NumberMath: "Shift distance must be an integral type, but <value> (<cls>) was supplied"
            new LeakPattern(Pattern.compile(" but .* \\((.*)\\) was supplied$"),
                    " but " + REDACTED + " ($1) was supplied"),
            // DefaultTypeTransformation.compare: "Cannot compare <cls> with value '<v1>' and <cls> with value '<v2>'"
            new LeakPattern(Pattern.compile("Cannot compare (\\S+) with value '.*' and (\\S+) with value '.*'$"),
                    "Cannot compare $1 with value '" + REDACTED + "' and $2 with value '" + REDACTED + "'"),
            // ScriptBytecodeAdapter.toSpreadList: "Cannot spread the type <cls> with value <value>"
            new LeakPattern(Pattern.compile(
                    "Cannot spread the type (\\S+) with value .+?(, did you mean to use the spread-map operator instead\\?)?$"),
                    "Cannot spread the type $1 with value " + REDACTED + "$2"),
            // InvokerHelper.toSpreadMap: "Cannot spread the map <cls>, value <value>"
            new LeakPattern(Pattern.compile("Cannot spread the map (\\S+), value .+$"),
                    "Cannot spread the map $1, value " + REDACTED),
            // DefaultGroovyMethods.sprintf: "sprintf(String,<arg>)"
            new LeakPattern(Pattern.compile("sprintf\\(String,.*\\)$"), "sprintf(String," + REDACTED + ")"),
            // DefaultGroovyMethods range ops: "The argument (<to>) to downto() … the value (<self>) it's called on"
            new LeakPattern(Pattern.compile("The argument \\(.*\\) to (downto|upto|step)\\(\\)"),
                    "The argument (" + REDACTED + ") to $1()"),
            new LeakPattern(Pattern.compile("the value \\(.*\\) it's called on"),
                    "the value (" + REDACTED + ") it's called on"),
            // Catch-all for a missing-method "values: […]" tail embedded in a wrapping message
            new LeakPattern(Pattern.compile(" values: \\[.*\\]$"), " values: [" + REDACTED + "]")
    );

    private GroovyExceptionSanitizer() {
    }

    /**
     * Returns a copy of {@code throwable} in which values embedded by Groovy built-in exceptions
     * are replaced with {@link #REDACTED}.
     *
     * <p>The whole cause chain is sanitized. The returned throwable has the same class as the
     * original ({@code MissingMethodException} is returned as a {@code MissingMethodException}
     * subclass that no longer carries the original arguments), and the original stack trace is
     * preserved. If nothing is redacted, {@code throwable} itself is returned (same instance).
     *
     * @param throwable an exception thrown by Groovy script execution, or wrapping one
     * @param <T>       the throwable type
     * @return the sanitized throwable, or {@code throwable} when nothing was redacted
     */
    @SuppressWarnings("unchecked")
    public static <T extends Throwable> T sanitize(T throwable) {
        return (T) sanitizeChain(throwable, 0);
    }

    /**
     * Replaces the values embedded by Groovy built-in exceptions in a single message.
     *
     * @param message the message to redact
     * @return the redacted message, or {@code message} unchanged when no pattern matched
     */
    static String redactMessage(String message) {
        if (message == null) {
            return null;
        }
        String redacted = message;
        for (LeakPattern leakPattern : LEAKING_PATTERNS) {
            redacted = leakPattern.pattern().matcher(redacted).replaceAll(leakPattern.replacement());
        }
        return redacted.equals(message) ? message : redacted;
    }

    private static Throwable sanitizeChain(Throwable throwable, int depth) {
        if (throwable == null || depth > MAX_CAUSE_DEPTH) {
            return throwable;
        }
        Throwable originalCause = throwable.getCause();
        Throwable sanitizedCause = originalCause == null ? null : sanitizeChain(originalCause, depth + 1);
        if (throwable instanceof MissingMethodException missingMethod) {
            int argCount = missingMethod.getArguments() == null ? 0 : missingMethod.getArguments().length;
            if (argCount == 0 && sanitizedCause == originalCause) {
                // No stored arguments (zero-argument call, or an already redacted copy)
                return throwable;
            }
            return new RedactedMissingMethodException(missingMethod, sanitizedCause);
        }
        String message = throwable.getMessage();
        String redacted = redactMessage(message);
        boolean messageChanged = redacted != message;
        if (!messageChanged && sanitizedCause == originalCause) {
            return throwable;
        }
        Throwable copy = copyWithMessage(throwable, messageChanged ? redacted : message, sanitizedCause);
        if (copy == null) {
            return throwable;
        }
        copy.setStackTrace(throwable.getStackTrace());
        for (Throwable suppressed : throwable.getSuppressed()) {
            copy.addSuppressed(sanitizeChain(suppressed, depth + 1));
        }
        return copy;
    }

    /**
     * Re-instantiates {@code original}'s class with the given message and cause, preferring the
     * {@code (String, Throwable)} constructor when a cause is present.
     *
     * @return the copy, or {@code null} when the class offers no usable constructor
     */
    private static Throwable copyWithMessage(Throwable original, String message, Throwable cause) {
        if (cause != null) {
            Throwable copy = instantiate(original.getClass(), new Class<?>[]{String.class, Throwable.class},
                    message, cause);
            if (copy != null) {
                return copy;
            }
        }
        return instantiate(original.getClass(), new Class<?>[]{String.class}, message);
    }

    private static Throwable instantiate(Class<?> type, Class<?>[] parameterTypes, Object... args) {
        try {
            return (Throwable) type.getConstructor(parameterTypes).newInstance(args);
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    /**
     * Replaces the {@code values: […]} segment of a {@link MissingMethodException} message with
     * redacted placeholders, keeping everything else (method, class, argument types, suggestions).
     */
    private static String redactMissingMethodMessage(MissingMethodException e) {
        String message = e.getMessage();
        if (message == null) {
            return message;
        }
        int marker = message.indexOf(VALUES_MARKER);
        if (marker < 0) {
            return message;
        }
        int start = message.indexOf('[', marker + VALUES_MARKER.length());
        if (start < 0) {
            return message;
        }
        int end = message.lastIndexOf(']');
        if (end <= start) {
            return message;
        }
        int count = e.getArguments() == null ? 0 : e.getArguments().length;
        if (count == 0) {
            // No values to redact (a genuine zero-argument call, or an already redacted copy)
            return message;
        }
        StringBuilder values = new StringBuilder("[");
        for (int i = 0; i < count; i++) {
            if (i > 0) {
                values.append(", ");
            }
            values.append(REDACTED);
        }
        values.append(']');
        return message.substring(0, start) + values + message.substring(end + 1);
    }

    private record LeakPattern(Pattern pattern, String replacement) {
    }

    /**
     * A {@link MissingMethodException} whose message no longer carries the original argument
     * values. Groovy builds the {@code MissingMethodException} message dynamically from the
     * arguments it stores (there is no message-only constructor), so the arguments are dropped
     * and the message is fixed at construction time.
     */
    private static final class RedactedMissingMethodException extends MissingMethodException {

        @Serial
        private static final long serialVersionUID = -6117294305826143017L;

        private final String sanitizedMessage;

        RedactedMissingMethodException(MissingMethodException original, Throwable sanitizedCause) {
            super(original.getMethod(), original.getType(), new Object[0], original.isStatic());
            if (sanitizedCause != null) {
                initCause(sanitizedCause);
            }
            this.sanitizedMessage = redactMissingMethodMessage(original);
        }

        @Override
        public String getMessage() {
            return sanitizedMessage;
        }

        @Override
        public Object[] getArguments() {
            return new Object[0];
        }
    }
}
