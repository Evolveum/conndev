/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.spi;

import org.identityconnectors.framework.common.objects.OperationOptions;
import org.identityconnectors.framework.common.objects.filter.Filter;

public interface FilterAwareExecuteQueryProcessor extends ObjectSearchOperation {

    default boolean supports(Filter filter, OperationOptions operationOptions) {
        return supports(filter);
    }

    boolean supports(Filter filter);


}
