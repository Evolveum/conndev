package com.evolveum.polygon.conndev.build.api;

import com.evolveum.polygon.conndev.concepts.GroovyClosures;
import groovy.lang.Closure;
import groovy.lang.DelegatesTo;

/**
 * Root entry point for defining connector operations (create, read, update, delete, list, search).
 *
 * <p>An operation builder is configured per object class and provides access to
 * the specific builders for each ConnId operation type. Each operation builder
 * is configured via a Groovy closure delegated to the appropriate sub-builder.</p>
 *
 * <pre>{@code
 * schema.objectClass("User") {
 *     attribute("uid") ...
 *
 *     operations {
 *         create { ... }
 *         update { ... }
 *         search { ... }
 *     }
 * }
 * }</pre>
 */
public interface OperationBuilder {

    ObjectOperationSupportBuilder objectClass(String className);

    default ObjectOperationSupportBuilder objectClass(String className, @DelegatesTo(value = ObjectOperationSupportBuilder.class, strategy = Closure.DELEGATE_ONLY) Closure<?> closure) {
        return GroovyClosures.callAndReturnDelegate(closure, objectClass(className));
    }

}
