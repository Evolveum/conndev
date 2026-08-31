/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.spi;

import com.evolveum.polygon.conndev.concepts.SourceLocation;
import org.identityconnectors.framework.common.exceptions.ConnectorException;

/**
 * Helpers for building {@link ConnectorException} at the many
 * {@code catch (Exception e) { throw new ConnectorException(...) }} sites in connector code.
 */
public final class ConnectorExceptions {

    private ConnectorExceptions() {
    }

    /**
     * Builds a {@link ConnectorException} whose message includes {@code cause}'s own message and
     * a {@code [location=file:line]} field for the {@code .groovy} source location found in it
     * (if any), so both the reason and the location survive even if a caller only looks at the
     * top-level exception message and never unwraps the cause chain.
     *
     * <p>The cause's message is always appended (it is not sensitive). The {@code location} field
     * is appended only when {@link SourceLocation#fromException} finds one, which depends on
     * {@link com.evolveum.polygon.conndev.concepts.DevelopmentMode} being enabled, since it can
     * reveal internal script file paths.</p>
     *
     * <p>Returns rather than throws, so call sites keep the usual {@code throw} for the compiler's
     * flow analysis: {@code throw ConnectorExceptions.raiseException("...", e);}</p>
     *
     * @param message the base message
     * @param cause the exception to wrap, whose message and source location are appended
     * @return a ConnectorException with {@code message}, {@code cause}'s own message (if any), and
     *         a {@code [location=...]} field (if a location was found), wrapping {@code cause}
     */
    public static ConnectorException raiseException(String message, Throwable cause) {
        var text = message;
        if (cause != null && cause.getMessage() != null) {
            text += ": " + cause.getMessage();
        }
        return new MessageBuilder(text, cause).build();
    }

    /**
     * Builds the exception message as {@code "message [key=value][key=value]..."}, one bracket per
     * field. Kept as its own class (rather than a single method) so future fields (e.g.
     * {@code errorCode}, {@code statusCode}) can be added as additional fluent methods here without
     * changing the {@code raiseException} call sites' shape.
     */
    private static final class MessageBuilder {

        private final String message;
        private final Throwable cause;
        private final StringBuilder fields = new StringBuilder();

        private MessageBuilder(String message, Throwable cause) {
            this.message = message;
            this.cause = cause;
            var location = SourceLocation.fromException(cause);
            if (location != SourceLocation.UNKNOWN) {
                fields.append("[location=").append(location).append("]");
            }
        }

        private ConnectorException build() {
            var fullMessage = fields.isEmpty() ? message : message + " " + fields;
            return new ConnectorException(fullMessage, cause);
        }
    }
}
