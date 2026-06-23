package com.evolveum.polygon.conndev.spi;

import org.identityconnectors.framework.common.objects.Attribute;
import org.identityconnectors.framework.common.objects.ConnectorObject;
import org.identityconnectors.framework.common.objects.OperationOptions;

import java.util.Set;

public interface ObjectCreateOperation extends ObjectClassOperation {

    ConnectorObject create(Set<Attribute> createAttributes, OperationOptions options);

}
