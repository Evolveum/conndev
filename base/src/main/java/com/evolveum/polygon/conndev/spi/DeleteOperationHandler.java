/*
 * Copyright (c) 2026 Evolveum and contributors
 * 
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 * 
 */
package com.evolveum.polygon.conndev.spi;

import com.evolveum.polygon.conndev.api.ContextLookup;
import org.identityconnectors.framework.common.objects.OperationOptions;
import org.identityconnectors.framework.common.objects.Uid;

public interface DeleteOperationHandler {

    /** Executes one explicitly registered cleanup or primary deletion step. */
    void delete(Uid uid, OperationOptions options, ContextLookup context);
}
