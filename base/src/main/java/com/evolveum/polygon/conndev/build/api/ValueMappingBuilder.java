/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.build.api;

import com.evolveum.polygon.conndev.groovy.api.HelperFunctionsMixin;
import groovy.lang.Closure;
import groovy.lang.DelegatesTo;

public interface ValueMappingBuilder<C,P> {

    ValueMappingBuilder<C,P> deserialize(@DelegatesTo(value = DeserializationContext.class, strategy = Closure.DELEGATE_ONLY) Closure<C> closure);

    ValueMappingBuilder<C,P> serialize(Closure<P> closure);

    record DeserializationContext<P>(P value) implements HelperFunctionsMixin {

        public P getValue() {
            return value;
        }

    }
}
