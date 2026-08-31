/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.devtools.log;

import java.util.List;

/**
 * Failure data carried by {@code ERROR} events.
 *
 * @param message    failure message
 * @param stacktrace stack trace lines (may be null)
 */
public record ErrorPayload(String message, List<String> stacktrace) {
}
