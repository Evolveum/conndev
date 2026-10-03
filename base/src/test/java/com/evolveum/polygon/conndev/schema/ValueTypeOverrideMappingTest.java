/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.schema;

import com.evolveum.polygon.conndev.json.JsonSchemaValueMapping;
import com.evolveum.polygon.conndev.json.OpenApiValueMapping;
import com.evolveum.polygon.conndev.spi.ValueMapping;
import org.identityconnectors.common.security.GuardedString;
import org.testng.annotations.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.testng.Assert.expectThrows;

/**
 * Unit tests for {@link ValueTypeOverrideMapping} — the wrapper that bridges a mapping's
 * native ConnId type to an attribute's final one. Focus: the {@code GuardedString}
 * override (a {@code String}-backed JSON mapping behind a forced password attribute),
 * which previously failed the schema build with
 * "Unsupported override type combination: class ...GuardedString and Password".
 */
public class ValueTypeOverrideMappingTest {

    private static GuardedString password(String value) {
        return new GuardedString(value.toCharArray());
    }

    @Test
    public void guardedStringOverPasswordFormat_buildsAndReportsGuardedString() {
        ValueMapping<Object, JsonNode> mapping =
                ValueTypeOverrideMapping.of(GuardedString.class, OpenApiValueMapping.Password);

        assertThat(mapping.connIdType()).isEqualTo(GuardedString.class);
        assertThat(mapping.primaryWireType()).isEqualTo(OpenApiValueMapping.Password.primaryWireType());
    }

    @Test
    public void guardedStringOverPasswordFormat_deserializesStringToGuardedString() {
        ValueMapping<Object, JsonNode> mapping =
                ValueTypeOverrideMapping.of(GuardedString.class, OpenApiValueMapping.Password);

        Object value = mapping.toConnIdValue(JsonNodeFactory.instance.stringNode("secret"));

        assertThat(value).isInstanceOf(GuardedString.class);
        assertThat(value).isEqualTo(password("secret"));
    }

    @Test
    public void guardedStringOverPasswordFormat_serializesGuardedStringToString() {
        ValueMapping<Object, JsonNode> mapping =
                ValueTypeOverrideMapping.of(GuardedString.class, OpenApiValueMapping.Password);

        JsonNode wire = mapping.toWireValue(password("secret"));

        assertThat(wire.asText()).isEqualTo("secret");
    }

    @Test
    public void guardedStringOverPlainStringMapping_roundTrips() {
        ValueMapping<Object, JsonNode> mapping =
                ValueTypeOverrideMapping.of(GuardedString.class, JsonSchemaValueMapping.STRING);

        Object value = mapping.toConnIdValue(JsonNodeFactory.instance.stringNode("p@ss"));
        assertThat(value).isEqualTo(password("p@ss"));
        assertThat(mapping.toWireValue(password("p@ss")).asText()).isEqualTo("p@ss");
    }

    @Test
    public void stringOverGuardedString_roundTrips() {
        ValueMapping<Object, GuardedString> mapping =
                ValueTypeOverrideMapping.of(String.class, ValueMapping.identity(GuardedString.class));

        assertThat(mapping.connIdType()).isEqualTo(String.class);
        assertThat(mapping.toConnIdValue(password("secret"))).isEqualTo("secret");
        assertThat(mapping.toWireValue("secret")).isEqualTo(password("secret"));
    }

    @Test
    public void nullValuesRoundTripAsNull() {
        ValueMapping<Object, JsonNode> mapping =
                ValueTypeOverrideMapping.of(GuardedString.class, JsonSchemaValueMapping.STRING);

        assertThat(mapping.toWireValue(null)).isNull();
        assertThat(mapping.toConnIdValue(JsonNodeFactory.instance.nullNode())).isNull();
        assertThat(mapping.toConnIdValue(null)).isNull();
    }

    @Test
    public void unsupportedDelegateTypeForGuardedString_throws() {
        var exception = expectThrows(IllegalArgumentException.class,
                () -> ValueTypeOverrideMapping.of(GuardedString.class, JsonSchemaValueMapping.INTEGER));

        assertThat(exception.getMessage()).contains("Unsupported override type combination");
    }

    @Test
    public void unsupportedOverrideTypeStillThrows() {
        var exception = expectThrows(IllegalArgumentException.class,
                () -> ValueTypeOverrideMapping.of(Long.class, OpenApiValueMapping.Password));

        assertThat(exception.getMessage()).contains("Unsupported override type combination");
    }
}
