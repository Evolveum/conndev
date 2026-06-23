package com.evolveum.polygon.conndev.build;

import com.evolveum.polygon.conndev.spi.ObjectClassOperation;

public interface ObjectOperationBuilder<T extends ObjectClassOperation> {


    T build();

}
