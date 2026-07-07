/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */

package com.evolveum.polygon.conndev.build.api;

/**
 * Placeholder connector-level builder for GDSL script validation
 *
 * <p>Currently contains only commented-out code suggesting a future design direction.
 * This interface is not yet functional.</p>
 *
 * @see SchemaBuilder
 * @see ObjectClassSchemaBuilder
 * @see ObjectOperationSupportBuilder
 */
public interface ConnectorBuilder {

    /*
        @Override
        OB objectClass(String className);

        @Override
        OB objectClass(String className, @DelegatesTo(value = ObjectClassBuilder.class, strategy = Closure.DELEGATE_ONLY) Closure<?> closure);

        interface ObjectClassBuilder extends ObjectClassSchemaBuilder, ObjectOperationSupportBuilder {

        }

 */
}
