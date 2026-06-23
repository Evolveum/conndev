package com.evolveum.polygon.conndev.spi;

import org.identityconnectors.framework.common.objects.OperationOptions;
import org.identityconnectors.framework.common.objects.Uid;

public interface DeleteOperationHandler {

    void delete(Uid uid, OperationOptions options);
}
