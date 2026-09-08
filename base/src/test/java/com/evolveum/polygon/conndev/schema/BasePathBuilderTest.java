/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.schema;

import com.evolveum.polygon.conndev.api.AttributePath;
import com.evolveum.polygon.conndev.api.BasicJsonPathFormat;
import com.evolveum.polygon.conndev.api.JsonPointerFormat;
import com.evolveum.polygon.conndev.api.ParsingException;
import com.evolveum.polygon.conndev.concepts.DefinitionValue;
import org.testng.annotations.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.testng.Assert.assertThrows;

public class BasePathBuilderTest {

    @Test
    public void testBuild_requiresValue() {
        assertThrows("Expected IllegalStateException for missing value",
                IllegalStateException.class, new BasePathBuilder()::build);
    }

    @Test
    public void testBuild_blankValueRejected() {
        assertThrows("Expected IllegalStateException for blank value",
                IllegalStateException.class, () -> new BasePathBuilder().value("   ").build());
    }

    @Test
    public void testBuild_defaultFormatIsBasicJsonPath() {
        var declaration = new BasePathBuilder().value("$.a.b").build();

        assertThat(declaration.type().value()).isSameAs(BasicJsonPathFormat.INSTANCE);
        assertThat(declaration.type().origin()).isEqualTo(DefinitionValue.Origin.DEFAULT);
        assertThat(declaration.actual()).isEqualTo(AttributePath.of("a", "b"));
    }

    @Test
    public void testBuild_explicitFormatIsDeclared() {
        var declaration = new BasePathBuilder()
                .type(JsonPointerFormat.INSTANCE)
                .value("/a/0/b")
                .build();

        assertThat(declaration.type().origin()).isEqualTo(DefinitionValue.Origin.DECLARED);
        assertThat(declaration.actual()).isEqualTo(new AttributePath(List.of(
                new AttributePath.Attribute("a"),
                new AttributePath.IndexFilter(0),
                new AttributePath.Attribute("b"))));
    }

    @Test
    public void testBuild_doesNotParse() {
        // parsing is deferred to actual(); an invalid expression only fails there
        var declaration = new BasePathBuilder().value("$.items[?(@.id != 1)]").build();
        assertThrows("Expected ParsingException on first actual() access",
                ParsingException.class, declaration::actual);
    }
}
