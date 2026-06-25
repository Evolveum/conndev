/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.json;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.*;
import org.testng.annotations.Test;

import java.util.Arrays;
import java.util.Base64;

import static org.assertj.core.api.Assertions.*;

public class JsonSchemaValueMappingTest {

    // === STRING ===

    @Test
    public void testSTRING_toConnIdValue_fromText() {
        var mapper = new ObjectMapper();
        var textNode = (TextNode) mapper.createObjectNode()
                .put("field", "hello world")
                .get("field");

        var result = JsonSchemaValueMapping.STRING.toConnIdValue(textNode);

        assertThat(result).isEqualTo("hello world");
    }

    @Test
    public void testSTRING_toWireValue() {
        var result = JsonSchemaValueMapping.STRING.toWireValue("test value");

        assertThat(result).isNotNull().isInstanceOf(TextNode.class);
        assertThat(result.asText()).isEqualTo("test value");
    }

    // === INTEGER ===

    @Test
    public void testINTEGER_toConnIdValue_fromIntNode() {
        var mapper = new ObjectMapper();
        var intNode = (IntNode) mapper.createObjectNode()
                .put("field", 42)
                .get("field");

        var result = JsonSchemaValueMapping.INTEGER.toConnIdValue(intNode);

        assertThat(result).isEqualTo(42);
    }

    @Test
    public void testINTEGER_toConnIdValue_fromNumericNode() {
        var mapper = new ObjectMapper();
        var floatNode = (FloatNode) mapper.createObjectNode()
                .put("field", 99.9f)
                .get("field");

        var result = JsonSchemaValueMapping.INTEGER.toConnIdValue(floatNode);

        assertThat(result).isEqualTo(99);
    }

    @Test
    public void testINTEGER_toWireValue_fromInteger() {
        var result = JsonSchemaValueMapping.INTEGER.toWireValue(123);

        assertThat(result).isNotNull().isInstanceOf(IntNode.class);
        assertThat(result.asInt()).isEqualTo(123);
    }

    // === BOOLEAN ===

    @Test
    public void testBOOLEAN_toConnIdValue_fromBooleanNode() {
        var mapper = new ObjectMapper();
        var boolNode = (BooleanNode) mapper.createObjectNode()
                .put("field", true)
                .get("field");

        var result = JsonSchemaValueMapping.BOOLEAN.toConnIdValue(boolNode);

        assertThat((Boolean) result).isTrue();
    }

    @Test
    public void testBOOLEAN_toWireValue_fromBoolean() {
        var result = JsonSchemaValueMapping.BOOLEAN.toWireValue(false);

        assertThat(result).isNotNull().isInstanceOf(BooleanNode.class);
        assertThat(result.asBoolean()).isFalse();
    }

    // === NUMBER ===

    @Test
    public void testNUMBER_toConnIdValue() {
        var mapper = new ObjectMapper();
        var doubleNode = (DoubleNode) mapper.createObjectNode()
                .put("field", 3.14159)
                .get("field");

        var result = JsonSchemaValueMapping.NUMBER.toConnIdValue(doubleNode);

        assertThat(result).isNotNull().isInstanceOf(Number.class);
        assertThat(((Number) result).doubleValue()).isEqualTo(3.14159);
    }

    @Test
    public void testNUMBER_toWireValue() {
        var result = JsonSchemaValueMapping.NUMBER.toWireValue(2.718);

        assertThat(result).isNotNull().isInstanceOf(NumericNode.class);
        assertThat(result.asDouble()).isEqualTo(2.718);
    }

    // === BINARY ===

    @Test
    public void testBINARY_toConnIdValue_fromBinaryNode() {
        var mapper = new ObjectMapper();
        byte[] data = {(byte)0xDE, (byte)0xAD, (byte)0xBE, (byte)0xEF};
        // Just use text node base64 decoding to test BINARY.toConnIdValue
        var encoded = Base64.getEncoder().encodeToString(data);
        var textNode = (TextNode) mapper.createObjectNode()
                .put("field", encoded)
                .get("field");

        var result = JsonSchemaValueMapping.BINARY.toConnIdValue(textNode);

        assertThat(result).isNotNull().isInstanceOf(byte[].class);
        assertThat(Arrays.equals((byte[]) result, data)).isTrue();
    }

    @Test
    public void testBINARY_toConnIdValue_fromTextNode_base64() {
        var mapper = new ObjectMapper();
        byte[] original = {(byte)0x01, (byte)0x02, (byte)0x03};
        var encoded = Base64.getEncoder().encodeToString(original);
        var textNode = (TextNode) mapper.createObjectNode()
                .put("field", encoded)
                .get("field");

        var result = JsonSchemaValueMapping.BINARY.toConnIdValue(textNode);

        assertThat(result).isNotNull().isInstanceOf(byte[].class);
        assertThat(Arrays.equals((byte[]) result, original)).isTrue();
    }

    @Test
    public void testBINARY_toWireValue_fromByteArray() {
        byte[] data = {(byte)0xFF, (byte)0x00, (byte)0xAB};
        var result = JsonSchemaValueMapping.BINARY.toWireValue(data);

        assertThat(result).isNotNull().isInstanceOf(BinaryNode.class);
        assertThat(Arrays.equals(((BinaryNode) result).binaryValue(), data)).isTrue();
    }

    // === Error cases ===

    @Test
    public void testSTRING_toConnIdValue_wrongType_throws() {
        var mapper = new ObjectMapper();
        var intNode = (IntNode) mapper.createObjectNode()
                .put("field", 123)
                .get("field");

        try {
            JsonSchemaValueMapping.STRING.toConnIdValue(intNode);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage()).contains("Can not convert");
        }
    }

    @Test
    public void testSTRING_toConnIdValue_nullNode_returnsNull() {
        var nullNode = JsonNodeFactory.instance.nullNode();

        var result = JsonSchemaValueMapping.STRING.toConnIdValue(nullNode);

        assertThat(result).isNull();
    }

    // === from() static method ===

    @Test
    public void test_from_unknown_returnsNull() {
        assertThatThrownBy(() -> JsonSchemaValueMapping.from("unknown-type"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unknown-type");
    }

}