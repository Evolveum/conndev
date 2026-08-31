/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.devtools.log;

/** Types of events carried by structured log events. */
public enum EventType {

    /** Start of a ConnId operation entry. */
    OPERATION,

    /** Key/value detail attached to an operation entry. */
    DETAIL,

    /** Protocol-specific data (HTTP request/response, SQL query). */
    PROTOCOL,

    /** Successful end result of an operation entry. */
    RESULT,

    /** Failed end result of an operation entry. */
    ERROR,

    /** Standalone message without an operation entry. */
    MESSAGE
}
