package com.evolveum.polygon.conndev.build.spi;

import com.evolveum.polygon.conndev.build.api.ObjectClassOperationBuilder;
import com.evolveum.polygon.conndev.concepts.DefinitionValue;
import com.evolveum.polygon.conndev.concepts.FluentBuilder;
import com.evolveum.polygon.conndev.spi.CompositeObjectClassHandler;
import com.evolveum.polygon.conndev.spi.ObjectClassOperation;

public interface SpiObjectClassHandlerBuilder<B extends ObjectClassOperationBuilder<B,P>, P extends ObjectClassOperation>
        extends FluentBuilder<B, P> {

    boolean isEnabled();

    default boolean isDisabled() {
        return !isEnabled();
    }

    B enabled(DefinitionValue<Boolean> value);

}
