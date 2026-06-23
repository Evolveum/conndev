/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.groovy;

import com.evolveum.polygon.conndev.spi.CompositeObjectClassHandler;
import com.evolveum.polygon.conndev.spi.ObjectClassHandler;
import com.evolveum.polygon.conndev.build.*;
import com.evolveum.polygon.conndev.schema.BaseObjectClassDefinition;
import com.evolveum.polygon.conndev.spi.ObjectCreateOperation;
import com.evolveum.polygon.conndev.spi.ObjectDeleteOperation;
import com.evolveum.polygon.conndev.spi.ObjectSearchOperation;
import com.evolveum.polygon.conndev.spi.ObjectClassOperation;
import com.evolveum.polygon.conndev.spi.ObjectUpdateOperation;

import java.util.HashMap;
import java.util.Map;

public abstract class BaseObjectOperationSupportBuilder<
        S extends AbstractSearchOperationBuilder,
        C extends AbstractCreateOperationBuilder,
        U extends AbstractUpdateOperationBuilder,
        D extends AbstractDeleteOperationBuilder> implements ObjectOperationSupportBuilder {

    private final BaseObjectClassDefinition objectClass;
    final ConnectorContext context;

    ObjectClassHandler product;
    Map<Class<? extends ObjectClassOperation>, ObjectClassOperation> buildedOperations = new HashMap<>();


    public BaseObjectOperationSupportBuilder(ConnectorContext context, BaseObjectClassDefinition restObjectClass) {
        this.objectClass = restObjectClass;
        this.context = context;
    }

    @Override
    public ListOperationBuilder list() {
        // FIXME: Implement
        throw new UnsupportedOperationException("Not implemented yet.");
    }

    @Override
    public ReadOperationBuilder read() {
        // FIXME: Implement
        throw new UnsupportedOperationException("Not implemented yet.");
    }

    @Override
    public abstract S search();

    @Override
    public abstract C create();

    @Override
    public abstract U update();

    @Override
    public abstract D delete();

    public BaseObjectClassDefinition getObjectClass() {
        return objectClass;
    }


    public BaseObjectOperationSupportBuilder<S, C, U, D> search(ObjectSearchOperation processor) {
        registerOperation(ObjectSearchOperation.class, processor);
        return this;
    }

    protected <T extends ObjectClassOperation> void registerOperation(Class<T> operationType, T operation) {
        buildedOperations.put(operationType, operation);
    }

    public ObjectClassHandler build() {
        buildOperationIfEmpty(ObjectSearchOperation.class, search());
        buildOperationIfEmpty(ObjectCreateOperation.class, create());
        buildOperationIfEmpty(ObjectUpdateOperation.class, update());
        buildOperationIfEmpty(ObjectDeleteOperation.class, delete());

        return new CompositeObjectClassHandler(objectClass.objectClass(), buildedOperations);
    }

    private <T extends ObjectClassOperation, X extends ObjectOperationBuilder<T>> void buildOperationIfEmpty(Class<T> type, ObjectClassOperationBuilder<T> builder) {
        if (builder == null || buildedOperations.containsKey(type)) {
            // Skip building for now
            return;

        }
        buildedOperations.put(type, builder.build());
    }
}
