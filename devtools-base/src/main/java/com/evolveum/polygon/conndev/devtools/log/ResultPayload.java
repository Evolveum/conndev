/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.devtools.log;

/**
 * End result of an operation entry, carried by {@code RESULT} events.
 *
 * @param ok    whether the operation succeeded
 * @param value the result value (UID, count, sync token, …)
 */
public record ResultPayload(boolean ok, Object value) {
}
