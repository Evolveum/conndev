/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.spi;

import org.identityconnectors.framework.common.objects.ResultsHandler;

public interface BatchAwareResultHandler extends ResultsHandler {

    void batchFinished();

    static void batchFinished(ResultsHandler handler) {
        if (handler instanceof BatchAwareResultHandler resultHandler) {
            resultHandler.batchFinished();
        }
    }
}
