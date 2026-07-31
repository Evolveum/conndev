/*
 * Copyright (c) 2026 Evolveum and contributors
 * 
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 * 
 */
package com.evolveum.polygon.conndev.spi;

import com.evolveum.polygon.conndev.build.api.UpdateOperationBuilder;
import org.identityconnectors.framework.common.objects.AttributeDelta;
import org.identityconnectors.framework.common.objects.OperationOptions;

public interface UpdateOperationHandler extends AttributeAwareOperationHandler<AttributeDelta,UpdateOperationHandler> {


    /**
     * Returns True if Operation Handler requires to know origin state of supported attributes
     * Otherwise False. Returing True may result in issuing get operations before.
     * @return
     */
    boolean requiresOriginalState();


    void update(UpdateOperationBuilder.UpdateRequest request, OperationOptions options);
}
