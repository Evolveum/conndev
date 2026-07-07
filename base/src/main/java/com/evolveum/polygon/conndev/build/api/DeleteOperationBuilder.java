package com.evolveum.polygon.conndev.build.api;

import com.evolveum.polygon.conndev.spi.ObjectDeleteOperation;

/**
 * Builder for the delete operation.
 *
 * <p>Currently a marker interface with no additional methods beyond {@code build()}.
 * Extend if delete-specific configuration is needed in the future.</p>
 *
 * @see ObjectClassOperationBuilder
 */
public interface DeleteOperationBuilder extends ObjectClassOperationBuilder<ObjectDeleteOperation> {
}
