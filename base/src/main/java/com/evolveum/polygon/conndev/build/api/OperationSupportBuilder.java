/*
 * Copyright (c) 2026 Evolveum and contributors
 * 
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 * 
 */
package com.evolveum.polygon.conndev.build.api;

import com.evolveum.polygon.conndev.annotations.Script;
import com.evolveum.polygon.conndev.concepts.FluentBuilder;
import com.evolveum.polygon.conndev.concepts.GroovyClosures;
import com.evolveum.polygon.conndev.spi.CompositeObjectClassHandler;
import groovy.lang.Closure;
import groovy.lang.DelegatesTo;
import org.identityconnectors.framework.common.objects.ObjectClass;

import java.util.Map;

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
public interface OperationSupportBuilder<F extends OperationSupportBuilder<F,B>, B extends ObjectOperationSupportBuilder> extends FluentBuilder<F, Map<ObjectClass, CompositeObjectClassHandler>> {

    B objectClass(String className);

    default B objectClass(
            String className,
            @DelegatesTo(value = ObjectOperationSupportBuilder.class, strategy = Closure.DELEGATE_ONLY)
            @Script.Initialization
            Closure<?> closure) {
        return GroovyClosures.callAndReturnDelegate(closure, objectClass(className));
    }

}
