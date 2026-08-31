/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.logging;

import com.evolveum.polygon.conndev.devtools.log.CallerLocation;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Shared state of an operation entry, independent of the logger used to emit its events.
 *
 * <p>The state is registered as the current entry of the executing thread so that any
 * component can attach further events (protocol data, details, result) to the same entry.
 */
public final class OperationEntryState {

    private final String id;
    private final String parentId;
    private final OperationEntryState parent;
    private final String operation;
    private final String objectClass;
    private final CallerLocation location;
    private final long startTs;
    private final AtomicInteger sequence = new AtomicInteger();
    private volatile boolean completed;

    OperationEntryState(String parentId, OperationEntryState parent, String operation, String objectClass,
                        CallerLocation location, long startTs) {
        this.id = UUID.randomUUID().toString();
        this.parentId = parentId;
        this.parent = parent;
        this.operation = operation;
        this.objectClass = objectClass;
        this.location = location;
        this.startTs = startTs;
    }

    /**
     * Returns the unique identifier of the entry, shared by all of its log lines.
     *
     * @return the entry id
     */
    public String id() {
        return id;
    }

    /**
     * Returns the id of the enclosing entry, if this entry is nested.
     *
     * @return the parent entry id, or null
     */
    public String parentId() {
        return parentId;
    }

    /**
     * Returns the enclosing entry state, if this entry is nested.
     *
     * @return the parent state, or null
     */
    public OperationEntryState parent() {
        return parent;
    }

    /**
     * Returns the ConnId operation name.
     *
     * @return the operation name
     */
    public String operation() {
        return operation;
    }

    /**
     * Returns the object class the operation applies to.
     *
     * @return the object class name
     */
    public String objectClass() {
        return objectClass;
    }

    /**
     * Returns the caller location captured when the entry was started.
     *
     * @return the location
     */
    public CallerLocation location() {
        return location;
    }

    /**
     * Returns the timestamp of the first event, epoch millis.
     *
     * @return the start timestamp
     */
    public long startTs() {
        return startTs;
    }

    /**
     * Returns the next sequence number for the entry's events.
     *
     * @return the sequence number
     */
    int nextSequence() {
        return sequence.getAndIncrement();
    }

    /** Marks the entry as completed (a result or error event has been emitted). */
    void markCompleted() {
        completed = true;
    }

    /**
     * Checks whether the entry has completed.
     *
     * @return true if the entry is complete
     */
    public boolean isCompleted() {
        return completed;
    }
}
