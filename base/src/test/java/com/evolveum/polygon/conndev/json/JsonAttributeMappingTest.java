/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.json;

import com.evolveum.polygon.conndev.api.AttributePath;
import com.evolveum.polygon.conndev.api.AttributePathDeclaration;
import com.evolveum.polygon.conndev.api.BasicJsonPathFormat;
import com.evolveum.polygon.conndev.api.PathTypeException;
import org.identityconnectors.framework.common.objects.Attribute;
import org.identityconnectors.framework.common.objects.AttributeBuilder;
import org.testng.annotations.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.*;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.testng.Assert.assertThrows;

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
        assertThat((result instanceof StringNode)).isTrue();
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
        assertThat((result instanceof StringNode)).isTrue();
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

    @Test
    public void testDeclarationConstructor_exposesDeclarationAndResolves() throws Exception {
        var mapper = new ObjectMapper();
        var declaration = AttributePathDeclaration.of(
                BasicJsonPathFormat.INSTANCE, "$.items[?(@.id == 3)].name");
        var mapping = new JsonAttributeMapping(declaration, JsonSchemaValueMapping.STRING);

        assertThat(mapping.pathDeclaration()).isSameAs(declaration);
        assertThat(mapping.pathDeclaration().value().value()).isEqualTo("$.items[?(@.id == 3)].name");
        assertThat(mapping.path().components()).hasSize(3);

        var root = mapper.createObjectNode();
        var items = root.putArray("items");
        items.addObject().put("id", 1).put("name", "first");
        items.addObject().put("id", 3).put("name", "third");

        assertThat(mapping.attributeFromObject(root).asText()).isEqualTo("third");
    }

    // === Single Value from Attribute ===

    @Test
    public void testSingleValueFromAttribute_scalar() {
        var mapper = new ObjectMapper();
        var textNode = (StringNode) mapper.createObjectNode()
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
        assertThat(result.getFirst()).isEqualTo("one");
        assertThat(result.get(1)).isEqualTo("two");
        assertThat(result.get(2)).isEqualTo("three");
    }

    @Test
    public void testValuesFromAttribute_scalarNode() {
        var mapper = new ObjectMapper();
        var textNode = (StringNode) mapper.createObjectNode()
                .put("x", "single")
                .get("x");

        var mapping = new JsonAttributeMapping(
                AttributePath.of("field"),
                JsonSchemaValueMapping.STRING
        );

        List<Object> result = mapping.valuesFromAttribute(textNode);

        assertThat(result).isNotNull();
        assertThat(result.size()).isEqualTo(1);
        assertThat(result.getFirst()).isEqualTo("single");
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
        assertThat((parent.get("name") instanceof StringNode)).isTrue();
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

    // === Value Filters ===

    @Test
    public void testAttributeFromObject_valueFilterArray() {
        var mapper = new ObjectMapper();
        var root = mapper.createObjectNode();
        var items = root.putArray("items");
        items.addObject().put("name", "a");
        items.addObject().put("name", "b");

        var mapping = new JsonAttributeMapping(
                AttributePath.of("items").valueFilter("name", "b"),
                JsonSchemaValueMapping.STRING
        );

        var result = mapping.attributeFromObject(root);

        assertThat(result).isNotNull();
        assertThat(result.get("name").asText()).isEqualTo("b");
    }

    @Test
    public void testAttributeFromObject_valueFilterElementMissingKey() {
        var mapper = new ObjectMapper();
        var root = mapper.createObjectNode();
        var items = root.putArray("items");
        items.addObject().put("id", 1);
        items.addObject().put("other", "x");
        items.addObject().put("id", 2);

        var mapping = new JsonAttributeMapping(
                AttributePath.of("items").valueFilter("id", 2),
                JsonSchemaValueMapping.STRING
        );

        var result = mapping.attributeFromObject(root);

        assertThat(result).isNotNull();
        assertThat(result.get("id").asInt()).isEqualTo(2);
    }

    @Test
    public void testAttributeFromObject_valueFilterOnObjectNode() {
        var mapper = new ObjectMapper();
        var root = mapper.createObjectNode();
        var person = root.putObject("person");
        person.put("name", "John");

        var mapping = new JsonAttributeMapping(
                AttributePath.of("person").valueFilter("name", "John"),
                JsonSchemaValueMapping.STRING
        );

        var result = mapping.attributeFromObject(root);

        assertThat(result).isNotNull();
        assertThat(result.get("name").asText()).isEqualTo("John");
    }

    @Test
    public void testAttributeFromObject_valueFilterLongValue() {
        var mapper = new ObjectMapper();
        var root = mapper.createObjectNode();
        root.putArray("items").addObject().put("id", 3000000000L);

        var mapping = new JsonAttributeMapping(
                AttributePath.of("items").valueFilter("id", 3000000000L),
                JsonSchemaValueMapping.STRING
        );

        var result = mapping.attributeFromObject(root);

        assertThat(result).isNotNull();
        assertThat(result.get("id").asLong()).isEqualTo(3000000000L);
    }

    @Test
    public void testAttributeFromObject_valueFilterIntForLongNode() {
        var mapper = new ObjectMapper();
        var root = mapper.createObjectNode();
        root.putArray("items").addObject().put("id", 42L);

        var mapping = new JsonAttributeMapping(
                AttributePath.of("items").valueFilter("id", 42),
                JsonSchemaValueMapping.STRING
        );

        var result = mapping.attributeFromObject(root);

        assertThat(result).isNotNull();
        assertThat(result.get("id").asLong()).isEqualTo(42L);
    }

    @Test
    public void testAttributeFromObject_valueFilterDoubleValue() {
        var mapper = new ObjectMapper();
        var root = mapper.createObjectNode();
        root.putArray("items").addObject().put("rate", 1.5);

        var mapping = new JsonAttributeMapping(
                AttributePath.of("items").valueFilter("rate", 1.5),
                JsonSchemaValueMapping.STRING
        );

        var result = mapping.attributeFromObject(root);

        assertThat(result).isNotNull();
        assertThat(result.get("rate").asDouble()).isEqualTo(1.5);
    }

    @Test
    public void testAttributeFromObject_valueFilterNoMatch() {
        var mapper = new ObjectMapper();
        var root = mapper.createObjectNode();
        var items = root.putArray("items");
        items.addObject().put("id", 1);
        items.addObject().put("id", 2);

        var mapping = new JsonAttributeMapping(
                AttributePath.of("items").valueFilter("id", 3),
                JsonSchemaValueMapping.STRING
        );

        var result = mapping.attributeFromObject(root);

        assertThat(result).isNull();
    }

    @Test
    public void testAttributeFromObject_emptyPath() {
        var mapper = new ObjectMapper();
        var root = mapper.createObjectNode().put("x", 1);

        var mapping = new JsonAttributeMapping(
                new AttributePath(List.of()),
                JsonSchemaValueMapping.STRING
        );

        var result = mapping.attributeFromObject(root);

        assertThat(result).isNotNull();
        assertThat(result.get("x").asInt()).isEqualTo(1);
    }

    // === Creating path nodes ===

    @Test
    public void testCreatingResolver_missingContainer() throws Exception {
        var mapper = new ObjectMapper();
        var root = mapper.createObjectNode();
        var path = AttributePath.of("user");

        var result = path.resolve(root, JsonAttributeMapping.creatingResolver());

        assertThat(result).isInstanceOf(ObjectNode.class);
        assertThat(root.get("user")).isSameAs(result);
    }

    @Test
    public void testCreatingResolver_existingContainerPreserved() throws Exception {
        var mapper = new ObjectMapper();
        var root = mapper.createObjectNode();
        root.putObject("user").put("existing", "value");
        var path = AttributePath.of("user");

        var result = path.resolve(root, JsonAttributeMapping.creatingResolver());

        assertThat(result).isSameAs(root.get("user"));
        assertThat(result.get("existing").asText()).isEqualTo("value");
    }

    @Test
    public void testCreatingResolver_nullContainerReplaced() throws Exception {
        var mapper = new ObjectMapper();
        var root = mapper.createObjectNode().putNull("user");
        var path = AttributePath.of("user");

        var result = path.resolve(root, JsonAttributeMapping.creatingResolver());

        assertThat(result).isInstanceOf(ObjectNode.class);
        assertThat(root.get("user")).isSameAs(result);
    }

    @Test
    public void testCreatingResolver_emptyPathReturnsRoot() throws Exception {
        var mapper = new ObjectMapper();
        var root = mapper.createObjectNode().put("x", 1);

        var result = new AttributePath(List.of()).resolve(root, JsonAttributeMapping.creatingResolver());

        assertThat(result).isSameAs(root);
    }

    @Test
    public void testCreatingResolver_growsArrayFromMissingObject() throws Exception {
        var mapper = new ObjectMapper();
        var root = mapper.createObjectNode();
        var path = AttributePath.of("items").firstValue();

        var result = path.resolve(root, JsonAttributeMapping.creatingResolver());

        var items = root.get("items");
        assertThat(items).isInstanceOf(ArrayNode.class);
        assertThat(items.size()).isEqualTo(1);
        assertThat(result).isSameAs(items.get(0));
        assertThat(result).isInstanceOf(ObjectNode.class);
    }

    @Test
    public void testCreatingResolver_growsArrayToIndex() throws Exception {
        var mapper = new ObjectMapper();
        var root = mapper.createObjectNode();
        var path = new AttributePath(List.of(new AttributePath.Attribute("items"), new AttributePath.IndexFilter(2)));

        var result = path.resolve(root, JsonAttributeMapping.creatingResolver());

        var items = root.get("items");
        assertThat(items).isInstanceOf(ArrayNode.class);
        assertThat(items.size()).isEqualTo(3);
        assertThat(result).isSameAs(items.get(2));
    }

    @Test
    public void testCreatingResolver_existingArrayElementReturned() throws Exception {
        var mapper = new ObjectMapper();
        var root = mapper.createObjectNode();
        root.putArray("items").addObject().put("x", 1);
        var path = AttributePath.of("items").firstValue();

        var result = path.resolve(root, JsonAttributeMapping.creatingResolver());

        assertThat(result).isSameAs(root.get("items").get(0));
        assertThat(result.get("x").asInt()).isEqualTo(1);
    }

    @Test
    public void testCreatingResolver_nullArrayElementReplaced() throws Exception {
        var mapper = new ObjectMapper();
        var root = mapper.createObjectNode();
        root.putArray("items").addNull();
        var path = AttributePath.of("items").firstValue();

        var result = path.resolve(root, JsonAttributeMapping.creatingResolver());

        assertThat(result).isInstanceOf(ObjectNode.class);
        assertThat(root.get("items").get(0)).isSameAs(result);
    }

    @Test
    public void testCreatingResolver_valueFilterCreatesPopulatedObject() throws Exception {
        var mapper = new ObjectMapper();
        var root = mapper.createObjectNode();
        var path = AttributePath.of("items").valueFilter("id", 3);

        var result = path.resolve(root, JsonAttributeMapping.creatingResolver());

        var items = root.get("items");
        assertThat(items).isInstanceOf(ArrayNode.class);
        assertThat(items.size()).isEqualTo(1);
        assertThat(result).isSameAs(items.get(0));
        assertThat(result.get("id").asInt()).isEqualTo(3);
    }

    @Test
    public void testCreatingResolver_valueFilterReturnsExistingMatch() throws Exception {
        var mapper = new ObjectMapper();
        var root = mapper.createObjectNode();
        var items = root.putArray("items");
        items.addObject().put("id", 9);
        items.addObject().put("id", 3).put("x", "kept");
        var path = AttributePath.of("items").valueFilter("id", 3);

        var result = path.resolve(root, JsonAttributeMapping.creatingResolver());

        assertThat(result).isSameAs(items.get(1));
        assertThat(result.get("x").asText()).isEqualTo("kept");
        assertThat(items.size()).isEqualTo(2);
    }

    @Test
    public void testCreatingResolver_valueFilterAppendsWhenNoMatch() throws Exception {
        var mapper = new ObjectMapper();
        var root = mapper.createObjectNode();
        var items = root.putArray("items");
        items.addObject().put("id", 9);
        var path = AttributePath.of("items").valueFilter("id", 3);

        var result = path.resolve(root, JsonAttributeMapping.creatingResolver());

        assertThat(items.size()).isEqualTo(2);
        assertThat(result).isSameAs(items.get(1));
        assertThat(result.get("id").asInt()).isEqualTo(3);
    }

    @Test
    public void testCreatingResolver_filterPopulatesTypedValues() throws Exception {
        var mapper = new ObjectMapper();
        var root = mapper.createObjectNode();
        var filter = new AttributePath.SimpleValueFilter(Map.of(
                "id", 3000000000L,
                "rate", 1.5,
                "active", true,
                "kind", "admin",
                "amount", new BigDecimal("100"),
                "big", new BigInteger("123456789012345678901234567890")));
        var path = new AttributePath(List.of(new AttributePath.Attribute("items"), filter));

        var result = path.resolve(root, JsonAttributeMapping.creatingResolver());

        assertThat(result.get("id")).isInstanceOf(LongNode.class);
        assertThat(result.get("id").asLong()).isEqualTo(3000000000L);
        assertThat(result.get("rate").asDouble()).isEqualTo(1.5);
        assertThat(result.get("active").asBoolean()).isTrue();
        assertThat(result.get("kind").asText()).isEqualTo("admin");
        assertThat(result.get("amount")).isInstanceOf(DecimalNode.class);
        assertThat(result.get("amount").decimalValue()).isEqualByComparingTo(new BigDecimal("100"));
        assertThat(result.get("big").bigIntegerValue())
                .isEqualTo(new BigInteger("123456789012345678901234567890"));
    }

    @Test
    public void testCreatingResolver_nestedIndexFilters() throws Exception {
        var mapper = new ObjectMapper();
        var root = mapper.createObjectNode();
        var path = new AttributePath(List.of(
                new AttributePath.Attribute("a"),
                new AttributePath.IndexFilter(0),
                new AttributePath.IndexFilter(0)));

        var result = path.resolve(root, JsonAttributeMapping.creatingResolver());

        var a = root.get("a");
        assertThat(a).isInstanceOf(ArrayNode.class);
        assertThat(a.size()).isEqualTo(1);
        assertThat(a.get(0)).isInstanceOf(ArrayNode.class);
        assertThat(result).isSameAs(a.get(0).get(0));
        assertThat(result).isInstanceOf(ObjectNode.class);
    }

    @Test
    public void testCreatingResolver_filterFollowedByIndexFilterPreservesCreatedElement() throws Exception {
        var mapper = new ObjectMapper();
        var root = mapper.createObjectNode();
        var path = new AttributePath(List.of(
                new AttributePath.Attribute("items"),
                new AttributePath.SimpleValueFilter(Map.of("id", 3)),
                new AttributePath.IndexFilter(0)));

        assertThrows(PathTypeException.class, () -> path.resolve(root, JsonAttributeMapping.creatingResolver()));

        var items = root.get("items");
        assertThat(items).isInstanceOf(ArrayNode.class);
        assertThat(items.size()).isEqualTo(1);
        assertThat(items.get(0).get("id").asInt()).isEqualTo(3);
    }

    @Test
    public void testCreatingResolver_filterFollowedByFilterPreservesCreatedElement() throws Exception {
        var mapper = new ObjectMapper();
        var root = mapper.createObjectNode();
        var path = new AttributePath(List.of(
                new AttributePath.Attribute("items"),
                new AttributePath.SimpleValueFilter(Map.of("id", 3)),
                new AttributePath.SimpleValueFilter(Map.of("name", "x"))));

        assertThrows(PathTypeException.class, () -> path.resolve(root, JsonAttributeMapping.creatingResolver()));

        var items = root.get("items");
        assertThat(items).isInstanceOf(ArrayNode.class);
        assertThat(items.size()).isEqualTo(1);
        assertThat(items.get(0).get("id").asInt()).isEqualTo(3);
    }

    @Test
    public void testCreatingResolver_valueFilterNullValuePopulatesNull() throws Exception {
        var mapper = new ObjectMapper();
        var root = mapper.createObjectNode();
        var values = new LinkedHashMap<String, Object>();
        values.put("deleted", null);
        var path = new AttributePath(List.of(
                new AttributePath.Attribute("items"),
                new AttributePath.SimpleValueFilter(values)));

        var result = path.resolve(root, JsonAttributeMapping.creatingResolver());

        var items = root.get("items");
        assertThat(items).isInstanceOf(ArrayNode.class);
        assertThat(items.size()).isEqualTo(1);
        assertThat(result).isSameAs(items.get(0));
        assertThat(result.get("deleted").isNull()).isTrue();
    }

    @Test
    public void testCreatingResolver_negativeIndexThrows() throws Exception {
        var mapper = new ObjectMapper();
        var root = mapper.createObjectNode();
        var path = new AttributePath(List.of(
                new AttributePath.Attribute("items"), new AttributePath.IndexFilter(-1)));

        assertThrows(PathTypeException.class, () -> path.resolve(root, JsonAttributeMapping.creatingResolver()));

        assertThat(root.get("items")).isInstanceOf(ObjectNode.class);
        assertThat(root.get("items").size()).isEqualTo(0);
    }

    @Test
    public void testAttributeFromObject_valueFilterNullValue() {
        var mapper = new ObjectMapper();
        var root = mapper.createObjectNode();
        var items = root.putArray("items");
        items.addObject().putNull("deleted");
        items.addObject().put("deleted", true);
        var values = new LinkedHashMap<String, Object>();
        values.put("deleted", null);

        var mapping = new JsonAttributeMapping(
                new AttributePath(List.of(
                        new AttributePath.Attribute("items"),
                        new AttributePath.SimpleValueFilter(values))),
                JsonSchemaValueMapping.STRING
        );

        var result = mapping.attributeFromObject(root);

        assertThat(result).isNotNull();
        assertThat(result.get("deleted").isNull()).isTrue();
    }

    @Test
    public void testCreatingResolver_wrongTypeObjectUnderIndexFilter() throws Exception {
        var mapper = new ObjectMapper();
        var root = mapper.createObjectNode();
        root.putObject("items").put("a", 1);
        var path = AttributePath.of("items").firstValue();

        assertThrows(PathTypeException.class, () -> path.resolve(root, JsonAttributeMapping.creatingResolver()));

        assertThat(root.get("items").get("a").asInt()).isEqualTo(1);
    }

    @Test
    public void testCreatingResolver_wrongTypeScalarUnderAttribute() throws Exception {
        var mapper = new ObjectMapper();
        var root = mapper.createObjectNode().put("x", "scalar");
        var path = AttributePath.of("x", "y");

        assertThrows(PathTypeException.class, () -> path.resolve(root, JsonAttributeMapping.creatingResolver()));

        assertThat(root.get("x").asText()).isEqualTo("scalar");
    }

    @Test
    public void testCreatingResolver_wrongTypeFilterOnNonMatchingObject() throws Exception {
        var mapper = new ObjectMapper();
        var root = mapper.createObjectNode();
        root.putObject("x").put("a", 1);
        var path = AttributePath.of("x").valueFilter("id", 1);

        assertThrows(PathTypeException.class, () -> path.resolve(root, JsonAttributeMapping.creatingResolver()));

        assertThat(root.get("x").get("a").asInt()).isEqualTo(1);
    }

    @Test
    public void testCreatingResolver_wrongTypeArrayUnderAttribute() throws Exception {
        var mapper = new ObjectMapper();
        var root = mapper.createObjectNode();
        root.putArray("items").add(1);
        var path = AttributePath.of("items", "name");

        assertThrows(PathTypeException.class, () -> path.resolve(root, JsonAttributeMapping.creatingResolver()));

        assertThat(root.get("items").size()).isEqualTo(1);
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