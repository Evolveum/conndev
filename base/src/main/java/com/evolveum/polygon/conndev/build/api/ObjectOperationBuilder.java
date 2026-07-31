/*
 * Copyright (c) 2026 Evolveum and contributors
 * 
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 * 
 */
package com.evolveum.polygon.conndev.build.api;

import com.evolveum.polygon.conndev.spi.ObjectClassOperation;

public interface ObjectOperationBuilder<T extends ObjectClassOperation> {


    T build();

}
