/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.devtools.yaml.config;

import com.evolveum.polygon.conndev.devtools.yaml.fixtures.FixtureMappingBuilder;
import com.evolveum.polygon.conndev.devtools.yaml.shape.DelegatedObjectShape;
import com.evolveum.polygon.conndev.devtools.yaml.shape.LeafShape;
import com.evolveum.polygon.conndev.devtools.yaml.shape.LeafShape.LeafType;
import com.evolveum.polygon.conndev.devtools.yaml.shape.ListShape;
import com.evolveum.polygon.conndev.devtools.yaml.shape.MapShape;
import com.evolveum.polygon.conndev.devtools.yaml.shape.ObjectShape;
import com.evolveum.polygon.conndev.devtools.yaml.shape.ScriptShape;
import com.evolveum.polygon.conndev.devtools.yaml.shape.UnionShape;
import com.evolveum.polygon.conndev.devtools.yaml.shape.YamlShape;
import org.testng.annotations.Test;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The shape-file DSL parses into the shape model. */
public class ShapeDslTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static YamlShape parse(String json) {
        return ShapeDsl.parse(MAPPER.readTree(json), "test");
    }

    @Test
    public void anObjectParsesPropertiesRequiredAndDescription() {
        ObjectShape shape = (ObjectShape) parse("""
                {
                  "type": "object",
                  "description": "a block",
                  "properties": {
                    "name": { "type": "string" },
                    "enabled": { "type": "boolean" }
                  },
                  "required": ["name"]
                }
                """);

        assertThat(shape.properties()).containsKeys("name", "enabled");
        assertThat(shape.properties().get("name").required()).isTrue();
        assertThat(shape.properties().get("enabled").required()).isFalse();
        assertThat(shape.description()).isEqualTo("a block");
    }

    @Test
    public void aListParsesItsItem() {
        ListShape shape = (ListShape) parse("""
                { "type": "list", "item": { "type": "string" } }
                """);

        assertThat(shape.item()).isInstanceOf(LeafShape.class);
    }

    @Test
    public void aMapParsesValueRestrictedKeysAndNullableValue() {
        MapShape shape = (MapShape) parse("""
                {
                  "type": "map",
                  "value": { "type": "string" },
                  "keys": ["UID", "NAME"],
                  "nullableValue": true
                }
                """);

        assertThat(shape.restrictedKeys()).containsExactly("UID", "NAME");
        assertThat(shape.nullableValue()).isTrue();
    }

    @Test
    public void aOneOfParsesItsAlternatives() {
        UnionShape shape = (UnionShape) parse("""
                {
                  "type": "oneOf",
                  "items": [
                    { "type": "string" },
                    { "type": "object", "properties": { "value": { "type": "string" } } }
                  ]
                }
                """);

        assertThat(shape.alternatives()).hasSize(2);
        assertThat(shape.alternatives().get(0)).isInstanceOf(LeafShape.class);
        assertThat(shape.alternatives().get(1)).isInstanceOf(ObjectShape.class);
    }

    @Test
    public void aStringCarriesItsEnum() {
        LeafShape shape = (LeafShape) parse("""
                { "type": "string", "enum": ["GET", "POST"], "default": "POST" }
                """);

        assertThat(shape.type()).isEqualTo(LeafType.STRING);
        assertThat(shape.enumValues()).containsExactly("GET", "POST");
        assertThat(shape.defaultValue()).isEqualTo("POST");
    }

    @Test
    public void aScriptCarriesItsKindFlags() {
        ScriptShape shape = (ScriptShape) parse("""
                { "type": "script", "expression": true, "emptyBody": true }
                """);

        assertThat(shape.expression()).isTrue();
        assertThat(shape.emptyBody()).isTrue();
    }

    @Test
    public void inheritProducesADelegatedObject() {
        DelegatedObjectShape shape = (DelegatedObjectShape) parse("""
                {
                  "type": "object",
                  "properties": {
                    "method": { "type": "string" },
                    "path": { "type": "string" }
                  },
                  "inherit": "com.evolveum.polygon.conndev.devtools.yaml.fixtures.FixtureMappingBuilder"
                }
                """);

        assertThat(shape.delegateTypes()).containsExactly(FixtureMappingBuilder.class.getName());
        assertThat(shape.declared().properties()).containsKeys("method", "path");
        assertThat(shape.declared().sourceType()).isEqualTo(FixtureMappingBuilder.class.getName());
    }

    @Test
    public void inheritAndDelegateTypesAreMutuallyExclusive() {
        assertThatThrownBy(() -> parse("""
                {
                  "type": "object",
                  "inherit": "java.lang.String",
                  "delegateTypes": ["java.lang.String"]
                }
                """))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("mutually exclusive");
    }

    @Test
    public void anUnknownTypeIsRejected() {
        assertThatThrownBy(() -> parse("{ \"type\": \"frobnicator\" }"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("unknown shape type");
    }
}
