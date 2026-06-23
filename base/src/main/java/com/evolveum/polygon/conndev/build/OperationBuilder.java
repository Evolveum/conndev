package com.evolveum.polygon.conndev.build;

import com.evolveum.polygon.conndev.concepts.GroovyClosures;
import groovy.lang.Closure;
import groovy.lang.DelegatesTo;

public interface OperationBuilder {

    ObjectOperationSupportBuilder objectClass(String className);

    default ObjectOperationSupportBuilder objectClass(String className, @DelegatesTo(value = ObjectOperationSupportBuilder.class, strategy = Closure.DELEGATE_ONLY) Closure<?> closure) {
        return GroovyClosures.callAndReturnDelegate(closure, objectClass(className));
    }

}
