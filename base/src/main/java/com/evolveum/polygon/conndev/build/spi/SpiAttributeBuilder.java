/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.build.spi;

import com.evolveum.polygon.conndev.build.api.AttributeBuilder;
import com.evolveum.polygon.conndev.concepts.DefinitionValue;
import com.evolveum.polygon.conndev.concepts.Fluent;
import com.evolveum.polygon.conndev.concepts.FluentBuilder;

public interface SpiAttributeBuilder<B extends AttributeBuilder<B,P>, P> extends FluentBuilder<B, P> {

    B emulated(DefinitionValue<Boolean> emulated);

    B protocolName(DefinitionValue<String> protocolName);

    B remoteName(DefinitionValue<String> remoteName);

    B complexType(DefinitionValue<String> objectClass);

    @Override
    default P build() {
        throw new UnsupportedOperationException("Implementation required");
    }

    interface ConnIdMapping<F extends ConnIdMapping<F>> extends Fluent<F> {

        F name(DefinitionValue<String> name);

        F nativeName(DefinitionValue<String> name);

        F type(DefinitionValue<Class<?>> connIdType);

        F readable(DefinitionValue<Boolean> readable);

        F required(DefinitionValue<Boolean> required);

        F description(DefinitionValue<String> description);

        F returnedByDefault(DefinitionValue<Boolean> returnedByDefault);

        F multiValued(DefinitionValue<Boolean> multiValued);

        F creatable(DefinitionValue<Boolean> creatable);

        F updatable(DefinitionValue<Boolean> updatable);

        F roleInReference(DefinitionValue<String> detected);

        F referencedObjectClassName(DefinitionValue<String> complexType);

        F subtype(DefinitionValue<String> from);

        DefinitionValue<Class<?>> type();
    }
}