package com.evolveum.polygon.conndev.spi;

import org.identityconnectors.framework.common.objects.OperationOptions;
import org.identityconnectors.framework.common.objects.Uid;

public interface ObjectDeleteOperation extends ObjectClassOperation {

    void delete(Uid uid, OperationOptions options);
}
