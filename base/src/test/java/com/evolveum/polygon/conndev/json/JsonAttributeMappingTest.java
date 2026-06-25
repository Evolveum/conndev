/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.json;

import com.evolveum.polygon.conndev.api.AttributePath;
import com.evolveum.polygon.conndev.spi.ValueMapping;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.*;
import org.identityconnectors.framework.common.objects.Attribute;
import org.identityconnectors.framework.common.objects.AttributeBuilder;
import org.testng.annotations.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class JsonAttributeMappingTest {

    // === Attribute from Object ===

    @Test
    public void testAttributeFromObject_flatPath() {
        var mapper = new ObjectMapper();
        var root = mapper.createObjectNode();
        root.put("email", "test@example.com");

        var mapping = new JsonAttributeMapping(
                AttributePath.of("email"),
                JsonSchemaValueMapping.STRING
        );

        var result = mapping.attributeFromObject(root);

        assertThat(result).isNotNull();
        assertThat((result instanceof TextNode)).isTrue();
        assertThat(result.asText()).isEqualTo("test@example.com");
    }

    @Test
    public void testAttributeFromObject_nestedPath() {
        var mapper = new ObjectMapper();
        var root = mapper.createObjectNode();
        var user = root.putObject("user");
        user.put("name", "John");

        var mapping = new JsonAttributeMapping(
                AttributePath.of("user", "name"),
                JsonSchemaValueMapping.STRING
        );

        var result = mapping.attributeFromObject(root);

        assertThat(result).isNotNull();
        assertThat((result instanceof TextNode)).isTrue();
        assertThat(result.asText()).isEqualTo("John");
    }

    @Test
    public void testAttributeFromObject_nullPath() {
        var mapper = new ObjectMapper();
        var root = mapper.createObjectNode().put("x", 1);

        var mapping = new JsonAttributeMapping(
                (AttributePath) null,
                JsonSchemaValueMapping.INTEGER
        );

        var result = mapping.attributeFromObject(root);

        assertThat(result).isNull();
    }

    // === Single Value from Attribute ===

    @Test
    public void testSingleValueFromAttribute_scalar() {
        var mapper = new ObjectMapper();
        var textNode = (TextNode) mapper.createObjectNode()
                .put("x", "hello")
                .get("x");

        var mapping = new JsonAttributeMapping(
                AttributePath.of("field"),
                JsonSchemaValueMapping.STRING
        );

        var result = mapping.singleValueFromAttribute(textNode);

        assertThat(result).isEqualTo("hello");
    }

    // === Values from Attribute ===

    @Test
    public void testValuesFromAttribute_arrayNode() {
        var mapper = new ObjectMapper();
        var arrayNode = mapper.createArrayNode()
                .add("one")
                .add("two")
                .add("three");

        var mapping = new JsonAttributeMapping(
                AttributePath.of("field"),
                JsonSchemaValueMapping.STRING
        );

        List<Object> result = mapping.valuesFromAttribute(arrayNode);

        assertThat(result).isNotNull();
        assertThat(result.size()).isEqualTo(3);
        assertThat(result.get(0)).isEqualTo("one");
        assertThat(result.get(1)).isEqualTo("two");
        assertThat(result.get(2)).isEqualTo("three");
    }

    @Test
    public void testValuesFromAttribute_scalarNode() {
        var mapper = new ObjectMapper();
        var textNode = (TextNode) mapper.createObjectNode()
                .put("x", "single")
                .get("x");

        var mapping = new JsonAttributeMapping(
                AttributePath.of("field"),
                JsonSchemaValueMapping.STRING
        );

        List<Object> result = mapping.valuesFromAttribute(textNode);

        assertThat(result).isNotNull();
        assertThat(result.size()).isEqualTo(1);
        assertThat(result.get(0)).isEqualTo("single");
    }

    @Test
    public void testValuesFromAttribute_nullNode() {
        var nullNode = JsonNodeFactory.instance.nullNode();

        var mapping = new JsonAttributeMapping(
                AttributePath.of("field"),
                JsonSchemaValueMapping.STRING
        );

        List<Object> result = mapping.valuesFromAttribute(nullNode);

        assertThat(result).isNull();
    }

    // === To JSON Node ===

    @Test
    public void testToJsonNode_singleValue() {
        var mapper = new ObjectMapper();
        var parent = mapper.createObjectNode();

        var mapping = new JsonAttributeMapping(
                AttributePath.of("name"),
                JsonSchemaValueMapping.STRING
        );

        Attribute attr = AttributeBuilder.build("name", "John");
        mapping.toJsonNode(attr, parent);

        assertThat(parent.size()).isEqualTo(1);
        assertThat(parent.has("name")).isTrue();
        assertThat((parent.get("name") instanceof TextNode)).isTrue();
        assertThat(parent.get("name").asText()).isEqualTo("John");
    }

    @Test
    public void testToJsonNode_multiValue() {
        var mapper = new ObjectMapper();
        var parent = mapper.createObjectNode();

        var mapping = new JsonAttributeMapping(
                AttributePath.of("tags"),
                JsonSchemaValueMapping.STRING
        );

        Attribute attr = AttributeBuilder.build("tags", Arrays.asList("a", "b", "c"));
        mapping.toJsonNode(attr, parent);

        assertThat(parent.size()).isEqualTo(1);
        assertThat(parent.has("tags")).isTrue();
        assertThat((parent.get("tags") instanceof ArrayNode)).isTrue();
        assertThat(parent.get("tags").size()).isEqualTo(3);
    }

    @Test
    public void testToJsonNode_emptyValues() {
        var mapper = new ObjectMapper();
        var parent = mapper.createObjectNode();

        var mapping = new JsonAttributeMapping(
                AttributePath.of("empty"),
                JsonSchemaValueMapping.STRING
        );

        Attribute attr = AttributeBuilder.build("empty", Collections.emptyList());
        mapping.toJsonNode(attr, parent);

        assertThat(parent.size()).isEqualTo(0);
    }

    // === ConnId Type delegation ===

@Test
    public void testConnIdType_delegates() {
        var stringMapping = new JsonAttributeMapping(
                AttributePath.of("field"),
                JsonSchemaValueMapping.STRING
        );
    assertThat(stringMapping.connIdType()).isEqualTo(String.class);

        var intMapping = new JsonAttributeMapping(
                AttributePath.of("count"),
                JsonSchemaValueMapping.INTEGER
        );
    assertThat(intMapping.connIdType()).isEqualTo(Integer.class);

        var boolMapping = new JsonAttributeMapping(
                AttributePath.of("flag"),
                JsonSchemaValueMapping.BOOLEAN
        );
    assertThat(boolMapping.connIdType()).isEqualTo(Boolean.class);

        var numberMapping = new JsonAttributeMapping(
                AttributePath.of("rate"),
                JsonSchemaValueMapping.NUMBER
        );
    assertThat(numberMapping.connIdType()).isEqualTo(Number.class);

        // BINARY.connIdType() follows the source definition where BinaryNode.class is the third parameter
        var binaryMapping = new JsonAttributeMapping(
                AttributePath.of("data"),
                JsonSchemaValueMapping.BINARY
        );
    assertThat(binaryMapping.connIdType()).isEqualTo(BinaryNode.class);
    }
}