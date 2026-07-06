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

public interface UpdateOperationBuilder extends ObjectClassOperationBuilder<ObjectUpdateOperation> {

    interface AttributeSpecific<A extends AttributeValueFilter, T extends AttributeSpecific<A,T>> {

        A supportedAttribute(String attributeName);

        A supportedAttribute(String attributeName,
                             @DelegatesTo(value = AttributeValueFilter.class, strategy = Closure.DELEGATE_ONLY)
                             @Script.Initialization
                             Closure<?> closure);

        default T supportedAttributes(String... attributes) {
            for (var attribute : attributes) {
                supportedAttribute(attribute);
            }

            //noinspection unchecked
            return (T) this;
        }
    }


    interface AttributeValueFilter {

        /**
         * Value, which should be accepted and processed
         **/
        AttributeValueFilter value(Object value);

        AttributeValueFilter transition(Object oldValue, Object newValue);
    }

    record UpdateRequest(ObjectClass clazz, Uid uid, Collection<AttributeDelta> attributeDeltaSet, ConnectorObject before) {
    }

    record UpdateResponse(Uid uid, Set<AttributeDelta> changesApplied) {

    }
}
