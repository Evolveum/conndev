/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.build;

import com.evolveum.polygon.conndev.concepts.GroovyClosures;
import com.evolveum.polygon.conndev.spi.ObjectSearchOperation;
import groovy.lang.Closure;
import groovy.lang.DelegatesTo;

public interface SearchOperationBuilder extends ObjectClassOperationBuilder<ObjectSearchOperation> {

    AttributeResolverBuilder attributeResolver();

    default AttributeResolverBuilder attributeResolver(
            @DelegatesTo(value = AttributeResolverBuilder.class, strategy = Closure.DELEGATE_ONLY) Closure<?> definition) {
        return GroovyClosures.callAndReturnDelegate(definition, attributeResolver());
    }

    NormalizationBuilder normalize();

    default NormalizationBuilder normalize(@DelegatesTo(value = NormalizationBuilder.class, strategy = Closure.DELEGATE_ONLY) Closure<?> definition) {
        return GroovyClosures.callAndReturnDelegate(definition, normalize());
    }

    SearchScriptBuilder custom();

    default SearchScriptBuilder custom(@DelegatesTo(SearchScriptBuilder.class) Closure<?> definition) {
        return GroovyClosures.callAndReturnDelegate(definition, custom());
    }

}
