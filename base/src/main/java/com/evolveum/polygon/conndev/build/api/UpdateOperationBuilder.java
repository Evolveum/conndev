package com.evolveum.polygon.conndev.build.api;

import com.evolveum.polygon.conndev.annotations.Script;
import com.evolveum.polygon.conndev.spi.ObjectUpdateOperation;
import groovy.lang.Closure;
import groovy.lang.DelegatesTo;
import org.identityconnectors.framework.common.objects.AttributeDelta;
import org.identityconnectors.framework.common.objects.ConnectorObject;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.Uid;

import java.util.Collection;
import java.util.Set;

/**
 * Builder for configuring the update operation on an object class.
 *
 * <p>Update operation configuration includes per-attribute and per-value filtering
 * via {@link AttributeSpecific} and {@link AttributeValueFilter} interfaces.</p>
 */
public interface UpdateOperationBuilder extends ObjectClassOperationBuilder<ObjectUpdateOperation> {

    /**
     * Supports attribute-specific filtering for update operations.
     *
     * <p>Attributes can be filtered based on name, value, or value transition (before/after).
     * This is useful for selective updates where only certain attributes should trigger changes.</p>
     */
    interface AttributeSpecific<A extends AttributeValueFilter, T extends AttributeSpecific<A,T>> {

        /**
         * Marks an attribute as supported for filtering in update operations.
         *
         * @param attributeName the name of the supported attribute
         * @return an attribute value filter for further configuration
         */
        A supportedAttribute(String attributeName);

        /**
         * Marks an attribute as supported via a closure.
         *
         * @param attributeName the name of the supported attribute
         * @param closure a closure that configures the {@link AttributeValueFilter}
         * @return an attribute value filter for further configuration
         */
        A supportedAttribute(String attributeName,
                              @DelegatesTo(value = AttributeValueFilter.class, strategy = Closure.DELEGATE_ONLY)
                              @Script.Initialization
                              Closure<?> closure);

        /**
         * Marks multiple attributes as supported for filtering.
         *
         * @param attributes the attribute names
         * @return this instance for chaining
         */
        default T supportedAttributes(String... attributes) {
            for (var attribute : attributes) {
                supportedAttribute(attribute);
            }

            //noinspection unchecked
            return (T) this;
        }
    }


    /**
     * Filter for values within a supported attribute during update operations.
     *
     * <p>Allows restricting updates to specific values or value transitions
     * (e.g., "only update if the old value was X and the new value is Y").</p>
     */
    interface AttributeValueFilter {

        /**
         * Specifies a single accepted value for this attribute.
         *
         * @param value the accepted value
         * @return this filter for chaining
         */
        AttributeValueFilter value(Object value);

        /**
         * Specifies an accepted transition from oldValue to newValue.
         *
         * @param oldValue the previous value (can be null for creates)
         * @param newValue the current value being set
         * @return this filter for chaining
         */
        AttributeValueFilter transition(Object oldValue, Object newValue);
    }

    /**
     * Represents an update request received by the connector.
     *
     * @param clazz the object class being updated
     * @param uid the unique identifier of the object
     * @param attributeDeltaSet the set of attribute deltas
     * @param before the object state before the update
     */
    record UpdateRequest(ObjectClass clazz, Uid uid, Collection<AttributeDelta> attributeDeltaSet, ConnectorObject before) {
    }

    /**
     * Represents the response after an update operation.
     *
     * @param uid the unique identifier of the updated object
     * @param changesApplied the set of deltas that were actually applied
     */
    record UpdateResponse(Uid uid, Set<AttributeDelta> changesApplied) {

    }
}
