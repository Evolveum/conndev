/*
 * Copyright (c) 2026 Evolveum and contributors
 * 
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 * 
 */
package com.evolveum.polygon.conndev.build.spi;

import com.evolveum.polygon.conndev.build.api.ObjectClassOperationBuilder;
import com.evolveum.polygon.conndev.concepts.DefinitionValue;
import com.evolveum.polygon.conndev.concepts.FluentBuilder;
import com.evolveum.polygon.conndev.spi.ObjectClassOperation;

public interface SpiObjectClassHandlerBuilder<B extends ObjectClassOperationBuilder<B,P>, P extends ObjectClassOperation>
        extends FluentBuilder<B, P> {

    boolean isEnabled();

    default boolean isDisabled() {
        return !isEnabled();
    }

    B enabled(DefinitionValue<Boolean> value);

}
