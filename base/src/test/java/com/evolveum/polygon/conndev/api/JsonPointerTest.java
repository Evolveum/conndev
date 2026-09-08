/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.api;

import com.evolveum.polygon.conndev.json.JsonAttributeMapping;
import org.testng.annotations.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.testng.Assert.assertThrows;

public class JsonPointerTest {

    private static final JsonPointerFormat INSTANCE = JsonPointerFormat.INSTANCE;
    // ==================== Parsing ====================

    @Test
    public void testParse_root() {
        assertThat(INSTANCE.parse("")).isEqualTo(new AttributePath(List.of()));
    }

    @Test
    public void testParse_singleMember() {
        assertThat(INSTANCE.parse("/userName")).isEqualTo(AttributePath.of("userName"));
    }

    @Test
    public void testParse_nestedMembers() {
        assertThat(INSTANCE.parse("/name/givenName")).isEqualTo(AttributePath.of("name", "givenName"));
    }

    @Test
    public void testParse_index() {
        assertThat(INSTANCE.parse("/emails/0/value"))
                .isEqualTo(new AttributePath(List.of(
                        new AttributePath.Attribute("emails"),
                        new AttributePath.IndexFilter(0),
                        new AttributePath.Attribute("value"))));
    }

    @Test
    public void testParse_escapedSlash() {
        assertThat(INSTANCE.parse("/a~1b")).isEqualTo(AttributePath.of("a/b"));
    }

    @Test
    public void testParse_escapedTilde() {
        assertThat(INSTANCE.parse("/a~0b")).isEqualTo(AttributePath.of("a~b"));
    }

    @Test
    public void testParse_combinedEscapes() {
        assertThat(INSTANCE.parse("/a~1b~0c")).isEqualTo(AttributePath.of("a/b~c"));
    }

    @Test
    public void testParse_unescapedTilde() {
        assertThat(INSTANCE.parse("/~tilde")).isEqualTo(AttributePath.of("~tilde"));
    }

    @Test
    public void testParse_leadingZeroTokenIsMemberName() {
        assertThat(INSTANCE.parse("/01")).isEqualTo(AttributePath.of("01"));
        assertThat(INSTANCE.parse("/a/01"))
                .isEqualTo(new AttributePath(List.of(
                        new AttributePath.Attribute("a"),
                        new AttributePath.Attribute("01"))));
    }

    @Test
    public void testParse_uriAsMemberName() {
        var path = INSTANCE.parse("/urn:ietf:params:scim:schemas:extension:enterprise:2.0:User/employeeNumber");
        assertThat(path).isEqualTo(AttributePath.of(
                "urn:ietf:params:scim:schemas:extension:enterprise:2.0:User",
                "employeeNumber"));
    }

    @Test
    public void testParse_emptyMemberName() {
        assertThat(INSTANCE.parse("/")).isEqualTo(new AttributePath(List.of(
                new AttributePath.Attribute(""))));
        assertThat(INSTANCE.parse("//a")).isEqualTo(new AttributePath(List.of(
                new AttributePath.Attribute(""),
                new AttributePath.Attribute("a"))));
    }

    @Test
    public void testParse_invalidInputs() {
        for (var input : new String[]{
                null,
                "userName",
                "a/b",
                "/99999999999999999999",
        }) {
            assertThrows("Expected ParsingException for: " + input, ParsingException.class,
                    () -> INSTANCE.parse(input));
        }
    }

    // ==================== Serialization ====================

    @Test
    public void testSerialize_emptyPath() {
        assertThat(INSTANCE.serialize(new AttributePath(List.of()))).isEmpty();
    }

    @Test
    public void testSerialize_members() {
        assertThat(INSTANCE.serialize(AttributePath.of("userName"))).isEqualTo("/userName");
        assertThat(INSTANCE.serialize(AttributePath.of("name", "givenName"))).isEqualTo("/name/givenName");
    }

    @Test
    public void testSerialize_index() {
        assertThat(INSTANCE.serialize(AttributePath.of("emails").firstValue().child("value")))
                .isEqualTo("/emails/0/value");
    }

    @Test
    public void testSerialize_escaping() {
        assertThat(INSTANCE.serialize(AttributePath.of("a/b"))).isEqualTo("/a~1b");
        assertThat(INSTANCE.serialize(AttributePath.of("a~b"))).isEqualTo("/a~0b");
        assertThat(INSTANCE.serialize(AttributePath.of("a/b~c"))).isEqualTo("/a~1b~0c");
    }

    @Test
    public void testSerialize_extension() {
        var path = AttributePath.of(
                new AttributePath.Extension("urn:ietf:params:scim:schemas:core:2.0:User"),
                new AttributePath.Attribute("employeeNumber"));
        assertThat(INSTANCE.serialize(path))
                .isEqualTo("/urn:ietf:params:scim:schemas:core:2.0:User/employeeNumber");
    }

    // ==================== Serialization errors ====================

    @Test
    public void testSerialize_invalidPaths() {
        assertThrows("Expected exception for null path", AttributePathFormatException.class,
                () -> INSTANCE.serialize(null));
        assertThrows("Expected exception for negative index", AttributePathFormatException.class,
                () -> INSTANCE.serialize(
                        new AttributePath(List.of(new AttributePath.Attribute("a"), new AttributePath.IndexFilter(-1)))));
        assertThrows("Expected exception for value filter", AttributePathFormatException.class,
                () -> INSTANCE.serialize(AttributePath.of("items").valueFilter("id", 1)));
    }

    // ==================== Round-trip ====================

    @Test
    public void testRoundTrip() {
        var paths = List.of(
                new AttributePath(List.of()),
                AttributePath.of("userName"),
                AttributePath.of("name", "givenName"),
                AttributePath.of("emails").firstValue().child("value"),
                new AttributePath(List.of(
                        new AttributePath.Attribute("a/b"),
                        new AttributePath.Attribute("c~d"))),
                new AttributePath(List.of(
                        new AttributePath.Attribute("01"),
                        new AttributePath.Attribute("x"))));
        for (var path : paths) {
            var serialized = INSTANCE.serialize(path);
            assertThat(INSTANCE.parse(serialized)).isEqualTo(path);
            assertThat(INSTANCE.serialize(INSTANCE.parse(serialized))).isEqualTo(serialized);
        }
    }

    @Test
    public void testRoundTrip_extensionBecomesAttribute() {
        var path = AttributePath.of(
                new AttributePath.Extension("urn:ietf:params:scim:schemas:core:2.0:User"),
                new AttributePath.Attribute("employeeNumber"));
        var serialized = INSTANCE.serialize(path);
        assertThat(serialized).isEqualTo("/urn:ietf:params:scim:schemas:core:2.0:User/employeeNumber");
        assertThat(INSTANCE.parse(serialized)).isEqualTo(AttributePath.of(
                "urn:ietf:params:scim:schemas:core:2.0:User",
                "employeeNumber"));
    }

    // ==================== Resolution ====================

    @Test
    public void testParsedPathResolves() {
        var mapper = new ObjectMapper();
        ObjectNode root = mapper.createObjectNode();
        var emails = root.putArray("emails");
        var work = emails.addObject();
        work.put("type", "work");
        work.put("value", "jdoe@example.com");

        var path = INSTANCE.parse("/emails/0/value");
        var resolved = path.resolve(root, JsonAttributeMapping.NULLABLE_PATH_RESOLVER);
        assertThat(resolved).isNotNull();
        assertThat(resolved.asText()).isEqualTo("jdoe@example.com");
    }
}
