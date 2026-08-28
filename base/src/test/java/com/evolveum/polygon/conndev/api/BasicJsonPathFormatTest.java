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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.testng.Assert.assertThrows;

public class BasicJsonPathFormatTest {

    private static final BasicJsonPathFormat INSTANCE = BasicJsonPathFormat.INSTANCE;
    // ==================== Parsing ====================

    @Test
    public void testParse_bareRoot() {
        assertThat(INSTANCE.parse("$")).isEqualTo(new AttributePath(List.of()));
    }

    @Test
    public void testParse_singleMember() {
        assertThat(INSTANCE.parse("$.userName")).isEqualTo(AttributePath.of("userName"));
    }

    @Test
    public void testParse_nestedMembers() {
        assertThat(INSTANCE.parse("$.name.givenName")).isEqualTo(AttributePath.of("name", "givenName"));
    }

    @Test
    public void testParse_memberNameCharacters() {
        assertThat(INSTANCE.parse("$.my-attr.sub_2")).isEqualTo(AttributePath.of("my-attr", "sub_2"));
    }

    @Test
    public void testParse_quotedMembers() {
        assertThat(INSTANCE.parse("$['name']['givenName']"))
                .isEqualTo(AttributePath.of("name", "givenName"));
        assertThat(INSTANCE.parse("$[\"name\"][\"givenName\"]"))
                .isEqualTo(AttributePath.of("name", "givenName"));
    }

    @Test
    public void testParse_index() {
        assertThat(INSTANCE.parse("$.items[0]"))
                .isEqualTo(new AttributePath(List.of(
                        new AttributePath.Attribute("items"),
                        new AttributePath.IndexFilter(0))));
        assertThat(INSTANCE.parse("$.items[0].name"))
                .isEqualTo(new AttributePath(List.of(
                        new AttributePath.Attribute("items"),
                        new AttributePath.IndexFilter(0),
                        new AttributePath.Attribute("name"))));
    }

    @Test
    public void testParse_filter() {
        var path = INSTANCE.parse("$.items[?(@.id == 1)]");
        assertThat(path).isEqualTo(new AttributePath(List.of(
                new AttributePath.Attribute("items"),
                new AttributePath.SimpleValueFilter(Map.of("id", 1)))));
    }

    @Test
    public void testParse_filterWithSubAttribute() {
        var path = INSTANCE.parse("$.emails[?(@.type == 'work')].value");
        assertThat(path).isEqualTo(new AttributePath(List.of(
                new AttributePath.Attribute("emails"),
                new AttributePath.SimpleValueFilter(Map.of("type", "work")),
                new AttributePath.Attribute("value"))));
    }

    @Test
    public void testParse_multiConditionFilter() {
        var path = INSTANCE.parse("$.items[?(@.id == 1 and @.name == 'x')]");
        assertThat(path).isEqualTo(new AttributePath(List.of(
                new AttributePath.Attribute("items"),
                new AttributePath.SimpleValueFilter(Map.of("id", 1, "name", "x")))));
    }

    @Test
    public void testParse_multiConditionFilterDoubleAmpersand() {
        var path = INSTANCE.parse("$.items[?(@.id == 1 && @.name == 'x')]");
        assertThat(path).isEqualTo(new AttributePath(List.of(
                new AttributePath.Attribute("items"),
                new AttributePath.SimpleValueFilter(Map.of("id", 1, "name", "x")))));
    }

    @Test
    public void testParse_quotedFilterKey() {
        var path = INSTANCE.parse("$.items[?(@['type'] == 'work')]");
        assertThat(path).isEqualTo(new AttributePath(List.of(
                new AttributePath.Attribute("items"),
                new AttributePath.SimpleValueFilter(Map.of("type", "work")))));
    }

    @Test
    public void testParse_filterValueTypes() {
        assertThat(INSTANCE.parse("$.items[?(@.active == true)]")).isEqualTo(new AttributePath(List.of(
                new AttributePath.Attribute("items"),
                new AttributePath.SimpleValueFilter(Map.of("active", true)))));
        var nullValues = new LinkedHashMap<String, Object>();
        nullValues.put("deleted", null);
        assertThat(INSTANCE.parse("$.items[?(@.deleted == null)]")).isEqualTo(new AttributePath(List.of(
                new AttributePath.Attribute("items"),
                new AttributePath.SimpleValueFilter(nullValues))));
        assertThat(INSTANCE.parse("$.items[?(@.rate == 1.5)]")).isEqualTo(new AttributePath(List.of(
                new AttributePath.Attribute("items"),
                new AttributePath.SimpleValueFilter(Map.of("rate", 1.5)))));
        assertThat(INSTANCE.parse("$.items[?(@.id == 3000000000)]")).isEqualTo(new AttributePath(List.of(
                new AttributePath.Attribute("items"),
                new AttributePath.SimpleValueFilter(Map.of("id", 3000000000L)))));
    }

    @Test
    public void testParse_escapedQuotedValue() {
        var path = INSTANCE.parse("$.items[?(@.name == 'a\\'b')]");
        assertThat(path).isEqualTo(new AttributePath(List.of(
                new AttributePath.Attribute("items"),
                new AttributePath.SimpleValueFilter(Map.of("name", "a'b")))));
    }

    @Test
    public void testParse_invalidInputs() {
        for (var input : new String[]{
                null,
                "",
                ".a",
                "a.b",
                "$.a.",
                "$.store..book",
                "$.store.*",
                "$.items[0:2]",
                "$.items[0,1]",
                "$.items[-1]",
                "$.items[]",
                "$.items[?@.id == 1]",
                "$.items[?(@ == 1)]",
                "$.items[?(@.id != 1)]",
                "$.items[?(@.id > 1)]",
                "$.items[?(@.id == 1 and @.id == 2)]",
                "$.items[?(@.id == 1 or @.x == 2)]",
                "$.items[?(@.id ==)]",
                "$.items[12345678901234567890123]",
                "$['\\uZZZZ']",
                "$.items[?(@.x == '\\uZZZZ')]",
                "$.items[?(@.x == '\\u12G4')]",
        }) {
            assertThrows("Expected ParsingException for: " + input, ParsingException.class,
                    () -> INSTANCE.parse(input));
        }
    }

    // ==================== Serialization ====================

    @Test
    public void testSerialize_emptyPath() {
        assertThat(INSTANCE.serialize(new AttributePath(List.of()))).isEqualTo("$");
    }

    @Test
    public void testSerialize_members() {
        assertThat(INSTANCE.serialize(AttributePath.of("userName"))).isEqualTo("$.userName");
        assertThat(INSTANCE.serialize(AttributePath.of("name", "givenName"))).isEqualTo("$.name.givenName");
    }

    @Test
    public void testSerialize_quotedMembers() {
        var path = new AttributePath(List.of(new AttributePath.Attribute("a b")));
        assertThat(INSTANCE.serialize(path)).isEqualTo("$['a b']");
        var path2 = new AttributePath(List.of(new AttributePath.Attribute("a'b")));
        assertThat(INSTANCE.serialize(path2)).isEqualTo("$['a\\'b']");
    }

    @Test
    public void testSerialize_extension() {
        var path = AttributePath.of(
                new AttributePath.Extension("urn:ietf:params:scim:schemas:core:2.0:User"),
                new AttributePath.Attribute("employeeNumber"));
        assertThat(INSTANCE.serialize(path))
                .isEqualTo("$['urn:ietf:params:scim:schemas:core:2.0:User'].employeeNumber");
    }

    @Test
    public void testSerialize_index() {
        assertThat(INSTANCE.serialize(AttributePath.of("items").firstValue().child("name")))
                .isEqualTo("$.items[0].name");
    }

    @Test
    public void testSerialize_filter() {
        var path = AttributePath.of("emails").valueFilter("type", "work").child("value");
        assertThat(INSTANCE.serialize(path)).isEqualTo("$.emails[?(@.type == 'work')].value");
    }

    @Test
    public void testSerialize_multiConditionFilter() {
        var values = new LinkedHashMap<String, Object>();
        values.put("price", 100);
        values.put("currency", "USD");
        var path = new AttributePath(List.of(
                new AttributePath.Attribute("costs"),
                new AttributePath.SimpleValueFilter(values)));
        assertThat(INSTANCE.serialize(path)).isEqualTo("$.costs[?(@.price == 100 and @.currency == 'USD')]");
    }

    @Test
    public void testSerialize_quotedFilterKey() {
        var values = new LinkedHashMap<String, Object>();
        values.put("a b", "x");
        var path = new AttributePath(List.of(
                new AttributePath.Attribute("items"),
                new AttributePath.SimpleValueFilter(values)));
        assertThat(INSTANCE.serialize(path)).isEqualTo("$.items[?(@['a b'] == 'x')]");
    }

    @Test
    public void testSerialize_filterValueTypes() {
        var values = new LinkedHashMap<String, Object>();
        values.put("active", true);
        values.put("deleted", null);
        values.put("rate", 1.5);
        var path = new AttributePath(List.of(
                new AttributePath.Attribute("items"),
                new AttributePath.SimpleValueFilter(values)));
        assertThat(INSTANCE.serialize(path))
                .isEqualTo("$.items[?(@.active == true and @.deleted == null and @.rate == 1.5)]");
    }

    // ==================== Serialization errors ====================

    @Test
    public void testSerialize_invalidPaths() {
        assertThrows("Expected exception for null path", AttributePathFormatException.class,
                () -> INSTANCE.serialize(null));
        assertThrows("Expected exception for negative index", AttributePathFormatException.class,
                () -> INSTANCE.serialize(
                        new AttributePath(List.of(new AttributePath.Attribute("a"), new AttributePath.IndexFilter(-1)))));
        var values = new LinkedHashMap<String, Object>();
        values.put("k", List.of(1));
        assertThrows("Expected exception for unsupported filter value type", AttributePathFormatException.class,
                () -> INSTANCE.serialize(new AttributePath(List.of(
                        new AttributePath.Attribute("items"),
                        new AttributePath.SimpleValueFilter(values)))));
        assertThrows("Expected exception for empty value filter", AttributePathFormatException.class,
                () -> INSTANCE.serialize(new AttributePath(List.of(
                        new AttributePath.Attribute("items"),
                        new AttributePath.SimpleValueFilter(Map.of())))));
    }

    // ==================== Round-trip ====================

    @Test
    public void testRoundTrip() {
        var values = new LinkedHashMap<String, Object>();
        values.put("price", 100);
        values.put("currency", "USD");
        var paths = List.of(
                new AttributePath(List.of()),
                AttributePath.of("userName"),
                AttributePath.of("name", "givenName"),
                AttributePath.of("items").firstValue().child("name"),
                AttributePath.of("emails").valueFilter("type", "work").child("value"),
                new AttributePath(List.of(
                        new AttributePath.Attribute("costs"),
                        new AttributePath.SimpleValueFilter(values))),
                new AttributePath(List.of(
                        new AttributePath.Attribute("a b"),
                        new AttributePath.Attribute("c d"))));
        for (var path : paths) {
            var serialized = INSTANCE.serialize(path);
            assertThat(INSTANCE.parse(serialized)).isEqualTo(path);
            assertThat(INSTANCE.serialize(INSTANCE.parse(serialized))).isEqualTo(serialized);
        }
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
        var home = emails.addObject();
        home.put("type", "home");
        home.put("value", "jdoe@home.example");

        var path = INSTANCE.parse("$.emails[?(@.type == 'work')].value");
        var resolved = path.resolve(root, JsonAttributeMapping.NULLABLE_PATH_RESOLVER);
        assertThat(resolved).isNotNull();
        assertThat(resolved.asText()).isEqualTo("jdoe@example.com");
    }
}
