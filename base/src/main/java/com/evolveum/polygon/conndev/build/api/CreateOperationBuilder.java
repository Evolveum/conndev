package com.evolveum.polygon.conndev.build.api;

import com.evolveum.polygon.conndev.spi.ObjectCreateOperation;

/**
 * Builder for the create operation.
 *
 * <p>Currently a marker interface with no additional methods beyond {@code build()}.
 * Extend if create-specific configuration is needed in the future.</p>
 *
 * @see ObjectClassOperationBuilder
 */
public interface CreateOperationBuilder extends ObjectClassOperationBuilder<ObjectCreateOperation> {
}
