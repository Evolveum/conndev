/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.spi;

import com.evolveum.polygon.conndev.api.ContextLookup;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.OperationOptions;
import org.identityconnectors.framework.common.objects.SyncResultsHandler;
import org.identityconnectors.framework.common.objects.SyncToken;

/**
 * Sync operation SPI for SQL connector.
 * Implements ConnId's SyncOp by delegating to this interface.
 *
 * <p>Follows the same pattern as {@link ObjectSearchOperation} where the
 * first parameter is always {@link ContextLookup} to give the handler
 * access to connection pool, query engine, and other runtime resources.</p>
 */
public interface ObjectSyncOperation extends ObjectClassOperation {

    /**
     * Execute a sync poll, emitting SyncDelta objects via the handler.
     *
     * @param token the token from the previous sync (null for full sync)
     * @param handler called for each change found
     * @param options operation options
     * @param context runtime context providing connection pool and query engine
     */
    void sync(SyncToken token, SyncResultsHandler handler,
              OperationOptions options, ContextLookup context);

    /**
     * Return the latest sync token for this object class.
     *
     * @param objectClass the object class
     * @return latest sync token, or null if no changes exist
     */
    SyncToken getLatestSyncToken();
}