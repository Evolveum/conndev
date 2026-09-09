/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.api;

import com.evolveum.polygon.conndev.concepts.DefinitionValue;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

public class AttributePathDeclarationTest {

    private static final AttributePath EXPECTED = new AttributePath(List.of(
            new AttributePath.Attribute("emails"),
            new AttributePath.SimpleValueFilter(Map.of("type", "work")),
            new AttributePath.Attribute("value")));

    // ==================== String mode ====================

    @Test
    public void testStringMode_parsesLazilyAndCaches() {
        var declaration = AttributePathDeclaration.of(BasicJsonPathFormat.INSTANCE,
                "$.emails[?(@.type == 'work')].value");

        assertThat(declaration.actual()).isEqualTo(EXPECTED);
        assertThat(declaration.actual()).isSameAs(declaration.actual());
    }

    @Test
    public void testStringMode_convenienceFactoryCapturesLocations() {
        var declaration = AttributePathDeclaration.of(JsonPointerFormat.INSTANCE, "/emails/0/value");

        assertThat(declaration.type().origin()).isEqualTo(DefinitionValue.Origin.DECLARED);
        assertThat(declaration.value().origin()).isEqualTo(DefinitionValue.Origin.DECLARED);
        assertThat(declaration.value().value()).isEqualTo("/emails/0/value");
        assertThat(declaration.actual()).isEqualTo(new AttributePath(List.of(
                new AttributePath.Attribute("emails"),
                new AttributePath.IndexFilter(0),
                new AttributePath.Attribute("value"))));
    }


    @Test
    public void testStringMode_invalidExpressionThrowsWithUserValueInContext() {
        var declaration = AttributePathDeclaration.of(BasicJsonPathFormat.INSTANCE,
                "$.items[?(@.id != 1)]");

        var e = catchThrowableOfType(declaration::actual, ParsingException.class);

        assertThat(e).isNotNull();
        assertThat(e).hasMessageContaining("Invalid JSONPath");
        assertThat(e.getContext()).contains("$.items[?(@.id != 1)]");
    }

    @Test
    public void testNullArgumentsRejected() {
        assertThatThrownBy(() -> AttributePathDeclaration.of((AttributePathFormat<String>) null, "x"))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> AttributePathDeclaration.of(BasicJsonPathFormat.INSTANCE, (String) null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> AttributePathDeclaration.of(JavaPathFormat.INSTANCE, (AttributePath) null))
                .isInstanceOf(NullPointerException.class);
    }

    // ==================== Java (no-op) format ====================

    @Test
    public void testJavaFormat_storesAttributePathAsValue() {
        var declaration = AttributePathDeclaration.of(JavaPathFormat.INSTANCE, EXPECTED);

        assertThat(declaration.type().value()).isSameAs(JavaPathFormat.INSTANCE);
        assertThat(declaration.value().value()).isSameAs(EXPECTED);
        assertThat(declaration.actual()).isSameAs(EXPECTED);
        assertThat(declaration.actual()).isSameAs(declaration.actual());
        assertThat(JavaPathFormat.INSTANCE.serialize(EXPECTED)).isSameAs(EXPECTED);
    }
}
