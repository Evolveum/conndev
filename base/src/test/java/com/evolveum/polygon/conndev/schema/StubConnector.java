/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.schema;

import org.identityconnectors.framework.spi.Configuration;
import org.identityconnectors.framework.spi.Connector;

public class StubConnector implements Connector {
    @Override
    public Configuration getConfiguration() {
        return null;
    }

    @Override
    public void init(Configuration configuration) {
    }

    @Override
    public void dispose() {
    }
}