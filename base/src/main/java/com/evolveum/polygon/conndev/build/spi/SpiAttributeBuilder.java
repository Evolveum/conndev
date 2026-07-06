/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.build.spi;

import com.evolveum.polygon.conndev.build.api.AttributeBuilder;
import com.evolveum.polygon.conndev.concepts.DefinitionValue;
import com.evolveum.polygon.conndev.concepts.FluentBuilder;

public interface SpiAttributeBuilder<B extends AttributeBuilder<B,P>, P> extends FluentBuilder<B, P> {

    B readable(DefinitionValue<Boolean> readable);

    B required(DefinitionValue<Boolean> required);

    B description(DefinitionValue<String> description);

    B returnedByDefault(DefinitionValue<Boolean> returnedByDefault);

    B multiValued(DefinitionValue<Boolean> multiValued);

    B creatable(DefinitionValue<Boolean> creatable);

    B updatable(DefinitionValue<Boolean> updatable);

    B emulated(DefinitionValue<Boolean> emulated);

    B protocolName(DefinitionValue<String> protocolName);

    B remoteName(DefinitionValue<String> remoteName);

    B complexType(DefinitionValue<String> objectClass);

    @Override
    default P build() {
        throw new UnsupportedOperationException("Implementation required");
    }
}