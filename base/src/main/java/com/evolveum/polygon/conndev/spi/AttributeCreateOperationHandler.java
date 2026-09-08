/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 */
package com.evolveum.polygon.conndev.spi;

import com.evolveum.polygon.conndev.api.ContextLookup;
import org.identityconnectors.framework.common.objects.Attribute;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.OperationOptions;
import org.identityconnectors.framework.common.objects.Uid;

import java.util.Set;

/** Initializes assigned attributes after primary creation, using create (not update) permissions. */
public interface AttributeCreateOperationHandler
        extends AttributeAwareOperationHandler<Attribute, AttributeCreateOperationHandler> {

    void create(Request request, OperationOptions options, ContextLookup context);

    record Request(ObjectClass objectClass, Uid uid, Set<Attribute> attributes) {
    }
}
