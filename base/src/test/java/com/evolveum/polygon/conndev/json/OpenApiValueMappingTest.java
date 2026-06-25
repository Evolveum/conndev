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

import java.math.BigDecimal;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

public class OpenApiValueMappingTest {

    // === DateTime ===

    @Test
    public void testDateTime_toWireValue() {
        var original = ZonedDateTime.now();
        var formatted = DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(original);
        var wireValue =  OpenApiValueMapping.DateTime.toWireValue(original);
        assertThat(formatted).isEqualTo(wireValue.asText());
    }

    @Test
    public void testDateTime_toConnIdValue() {
        var mapper = new ObjectMapper();
        ZonedDateTime original = ZonedDateTime.of(2025, 1, 15, 10, 30, 0, 0, ZoneId.of("UTC"));
        var formatted = DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(original);
        var textNode = (TextNode) mapper.createObjectNode()
                .put("x", formatted)
                .get("x");

        var result = OpenApiValueMapping.DateTime.toConnIdValue(textNode);

        assertThat(result).isNotNull().isInstanceOf(ZonedDateTime.class);
        var converted = (ZonedDateTime) result;
        assertThat(converted.getOffset()).isEqualTo(original.getOffset());
        // Verify they represent the same instant
        assertThat(converted.toInstant()).isEqualTo(original.toInstant());
    }

    // === Byte ===

    @Test
    public void testByte_toWireValue_base64() {
        byte[] data = new byte[]{(byte) 0x01, (byte) 0x02, (byte) 0x03};
        var result = OpenApiValueMapping.Byte.toWireValue(data);

        assertThat(result).isNotNull().isInstanceOf(TextNode.class);
        var expected = Base64.getEncoder().encodeToString(data);
        assertThat(result.asText()).isEqualTo(expected);
    }

    @Test
    public void testByte_toConnIdValue_base64() {
        var mapper = new ObjectMapper();
        byte[] original = new byte[]{(byte) 0xAB, (byte) 0xCD};
        var encoded = Base64.getEncoder().encodeToString(original);
        var textNode = (TextNode) mapper.createObjectNode()
                .put("x", encoded)
                .get("x");

        var result = OpenApiValueMapping.Byte.toConnIdValue(textNode);

        assertThat(result).isNotNull().isInstanceOf(byte[].class);
        assertThat(Arrays.equals((byte[]) result, original)).isTrue();
    }

    // === Int64 ===

    @Test
    public void testInt64_toWireValue() {
        var result = OpenApiValueMapping.Int64.toWireValue(42L);

        assertThat(result).isNotNull().isInstanceOf(LongNode.class);
        assertThat(result.asLong()).isEqualTo(42L);
    }

    @Test
    public void testInt64_toConnIdValue_fromLongNode() {
        var mapper = new ObjectMapper();
        var resultNode = mapper.createObjectNode()
                .put("x", 12345L)
                .get("x");
        var longNode = (LongNode) resultNode;

        var result = OpenApiValueMapping.Int64.toConnIdValue(longNode);

        assertThat(result).isNotNull().isEqualTo(12345L);
    }

    @Test
    public void testInt64_toConnIdValue_fromNumericNode() {
        var mapper = new ObjectMapper();
        // FloatNode is also a NumericNode
        var floatNode = (FloatNode) mapper.createObjectNode()
                .put("x", 1.0f)
                .get("x");

        var result = OpenApiValueMapping.Int64.toConnIdValue(floatNode);

        assertThat(result).isNotNull().isEqualTo(1L);
    }

    @Test
    public void testInt64_toConnIdValue_nullNode() {
        var nullNode = JsonNodeFactory.instance.nullNode();

        var result = OpenApiValueMapping.Int64.toConnIdValue(nullNode);

        assertThat(result).isNull();
    }

    // === UUID ===

    @Test
    public void testUUID() {
        var uuidMapping = OpenApiValueMapping.Uuid;

        assertThat(uuidMapping.connIdType()).isEqualTo(String.class);
        assertThat(uuidMapping.primaryWireType()).isEqualTo(TextNode.class);
        assertThat(uuidMapping.baseMapping).isEqualTo(JsonSchemaValueMapping.STRING);

        var mapper = new ObjectMapper();
        var node = mapper.createObjectNode()
                .put("x", "550e8400-e29b-41d4-a716-446655440000")
                .get("x");
        var result = uuidMapping.toConnIdValue(node);
        assertThat(result).isEqualTo("550e8400-e29b-41d4-a716-446655440000");
    }

    // === Email ===

    @Test
    public void testEmail_delegatesToSTRING() {
        var emailMapping = OpenApiValueMapping.Email;

        assertThat(emailMapping.connIdType()).isEqualTo(String.class);
        assertThat(emailMapping.primaryWireType()).isEqualTo(TextNode.class);
        assertThat(emailMapping.baseMapping).isEqualTo(JsonSchemaValueMapping.STRING);

        var mapper = new ObjectMapper();
        var node = mapper.createObjectNode()
                .put("x", "test@example.com")
                .get("x");
        var result = emailMapping.toConnIdValue(node);
        assertThat(result).isEqualTo("test@example.com");
    }

    // === Decimal ===

    @Test
    public void testDecimal_toWireValue() {
        var bd = new BigDecimal("123.456");
        var result = OpenApiValueMapping.Decimal.toWireValue(bd);

        assertThat(result).isNotNull().isInstanceOf(DecimalNode.class);
        assertThat(result.decimalValue()).isEqualTo(bd);
    }

    @Test
    public void testDecimal_toConnIdValue() {
        var mapper = new ObjectMapper();
        var node = mapper.createObjectNode()
                .put("x", 123.456)
                .get("x");

        var result = OpenApiValueMapping.Decimal.toConnIdValue(node);

        assertThat(result).isNotNull().isEqualTo(new BigDecimal("123.456"));
    }

    // === Int32 ===

    @Test
    public void testInt32_toWireValue() {
        var result = OpenApiValueMapping.Int32.toWireValue(42);

        assertThat(result).isNotNull().isInstanceOf(IntNode.class);
        assertThat(result.asInt()).isEqualTo(42);
    }

    @Test
    public void testInt32_toConnIdValue() {
        var mapper = new ObjectMapper();
        var resultNode = mapper.createObjectNode()
                .put("x", 99)
                .get("x");
        var intNode = (IntNode) resultNode;

        var result = OpenApiValueMapping.Int32.toConnIdValue(intNode);

        assertThat(result).isNotNull().isEqualTo(99);
    }

    // === from() ===

    @Test
    public void test_from_format_prefersOpenapi() {
        Object result = OpenApiValueMapping.from("string", "date-time");

        assertThat(result).isEqualTo(OpenApiValueMapping.DateTime);
    }

    @Test
    public void test_from_unknown_format_fallback() {
        Object result = OpenApiValueMapping.from("integer", "unknown-format");
        assertThat(result).isEqualTo(JsonSchemaValueMapping.INTEGER);
    }

    @Test
    public void test_from_typeOnly() {
        Object result = OpenApiValueMapping.from("string", null);
        assertThat(result).isEqualTo(JsonSchemaValueMapping.STRING);
    }

    // === Double ===

    @Test
    public void testDouble_toWireValue() {
        var result = OpenApiValueMapping.Double.toWireValue(3.14);

        assertThat(result).isNotNull().isInstanceOf(DoubleNode.class);
        assertThat(result.asDouble()).isEqualTo(3.14);
    }

    @Test
    public void testDouble_toConnIdValue() {
        var mapper = new ObjectMapper();
        var node = mapper.createObjectNode()
                .put("x", 2.718)
                .get("x");

        var result = OpenApiValueMapping.Double.toConnIdValue(node);

        assertThat(result).isNotNull().isInstanceOf(Double.class).isEqualTo(2.718);
    }

    // === Binary ===

    @Test
    public void testBinary_openapi() {
        var binaryMapping = OpenApiValueMapping.Binary;

        assertThat(binaryMapping.baseMapping).isEqualTo(JsonSchemaValueMapping.BINARY);
        assertThat(binaryMapping.primaryWireType()).isEqualTo(TextNode.class);
        assertThat(binaryMapping.connIdType()).isEqualTo(byte[].class);
    }

    // === canConvert ===

    @Test
    public void test_canConvert_toConnId() {
        var mapper = new ObjectMapper();
        var textNode = (TextNode) mapper.createObjectNode()
                .put("x", "2025-01-01T00:00:00Z")
                .get("x");
        var result = OpenApiValueMapping.DateTime.toConnIdValue(textNode);

        assertThat(result).isNotNull().isInstanceOf(ZonedDateTime.class);
    }

    // Additional OpenAPI-specific tests


    @Test
    public void testFloat_wireValue() {
        var mapper = new ObjectMapper();

        var result = OpenApiValueMapping.Float.toWireValue(1.5f);
        assertThat(result).isInstanceOf(FloatNode.class);

        var floatNode = (FloatNode) mapper.createObjectNode()
                .put("x", 2.5f)
                .get("x");
        var converted = OpenApiValueMapping.Float.toConnIdValue(floatNode);
        assertThat(converted).isInstanceOf(Float.class);
        assertThat((Float) converted).isEqualTo(2.5f);
    }

    @Test
    public void testUri() {
        var uriMapping = OpenApiValueMapping.Uri;
        assertThat(uriMapping.connIdType()).isEqualTo(String.class);
        assertThat(uriMapping.baseMapping).isEqualTo(JsonSchemaValueMapping.STRING);
    }

    @Test
    public void testSfToken() {
        var sfToken = OpenApiValueMapping.SfToken;
        assertThat(sfToken.connIdType()).isEqualTo(String.class);
        assertThat(sfToken.primaryWireType()).isEqualTo(TextNode.class);
    }
}