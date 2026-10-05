/*
 * Copyright (c) 2026 Evolveum and contributors
 * 
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 * 
 */
package com.evolveum.polygon.conndev.spi;

import com.evolveum.polygon.conndev.api.ContextLookup;
import com.evolveum.polygon.conndev.build.api.UpdateOperationBuilder;
import org.identityconnectors.framework.common.objects.AttributeDelta;
import org.identityconnectors.framework.common.objects.OperationOptions;
import org.identityconnectors.framework.common.objects.Uid;

import java.util.Set;

public interface UpdateOperationHandler extends AttributeAwareOperationHandler<AttributeDelta,UpdateOperationHandler> {


    /**
     * Returns True if Operation Handler requires to know origin state of supported attributes
     * Otherwise False. Returing True may result in issuing get operations before.
     * @return
     */
    boolean requiresOriginalState();

    /**
     * Applies the given attribute deltas and reports the changes that were actually applied.
     *
     * @param request the update request (carries the UID and, when present, the original state)
     * @param options the operation options
     * @param context the operation context
     * @return the response describing which deltas were applied; the reported deltas must be
     *         limited to the attributes this handler claimed via
     *         {@link #canHandle(java.util.Collection, OperationOptions)}
     */
    UpdateResponse update(UpdateOperationBuilder.UpdateRequest request, OperationOptions options, ContextLookup context);

    /**
     * Represents the response after an update operation.
     *
     * @param uid the unique identifier of the updated object
     * @param changesApplied the set of deltas that were actually applied
     */
    record UpdateResponse(Uid uid, Set<AttributeDelta> changesApplied) {

    }
}
