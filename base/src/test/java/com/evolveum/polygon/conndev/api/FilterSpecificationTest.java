/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.api;

import org.identityconnectors.framework.common.objects.AttributeBuilder;
import org.identityconnectors.framework.common.objects.filter.FilterBuilder;
import org.testng.annotations.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Matching semantics of {@link FilterSpecification}, with a focus on the concrete-value form
 * {@code attribute(name).eq(value)} used to declare filters that a search handler serves on
 * its own (e.g. routing a search to a dedicated endpoint such as {@code users/disabled}).
 */
public class FilterSpecificationTest {

    // ==================== concrete-value eq ====================

    @Test
    public void eqConcreteValueMatchesSingleValueEqualsFilter() {
        var spec = FilterSpecification.attribute("enabled").eq(false);

        assertThat(spec.matches(FilterBuilder.equalTo(AttributeBuilder.build("enabled", false)))).isTrue();
    }

    @Test
    public void eqConcreteValueMatchesBooleanTrueAsWell() {
        var spec = FilterSpecification.attribute("enabled").eq(true);

        assertThat(spec.matches(FilterBuilder.equalTo(AttributeBuilder.build("enabled", true)))).isTrue();
    }

    @Test
    public void eqConcreteValueMatchesStringValue() {
        var spec = FilterSpecification.attribute("status").eq("disabled");

        assertThat(spec.matches(FilterBuilder.equalTo(AttributeBuilder.build("status", "disabled")))).isTrue();
    }

    @Test
    public void eqConcreteValueRejectsDifferentValue() {
        var spec = FilterSpecification.attribute("enabled").eq(false);

        assertThat(spec.matches(FilterBuilder.equalTo(AttributeBuilder.build("enabled", true)))).isFalse();
    }

    @Test
    public void eqConcreteValueRejectsMultiValueFilter() {
        var spec = FilterSpecification.attribute("enabled").eq(false);

        assertThat(spec.matches(FilterBuilder.equalTo(AttributeBuilder.build("enabled", List.of(false, false))))).isFalse();
    }

    @Test
    public void eqConcreteValueRejectsNonEqualsFilter() {
        var spec = FilterSpecification.attribute("status").eq("disabled");

        assertThat(spec.matches(FilterBuilder.contains(AttributeBuilder.build("status", "disab")))).isFalse();
    }

    @Test
    public void eqConcreteValueRejectsDifferentAttribute() {
        var spec = FilterSpecification.attribute("enabled").eq(false);

        assertThat(spec.matches(FilterBuilder.equalTo(AttributeBuilder.build("active", false)))).isFalse();
    }

    // ==================== argument-less eq (unchanged) ====================

    @Test
    public void eqNoArgStillMatchesAnyValueEqualsFilter() {
        var spec = FilterSpecification.attribute("enabled").eq();

        assertThat(spec.matches(FilterBuilder.equalTo(AttributeBuilder.build("enabled", false)))).isTrue();
        assertThat(spec.matches(FilterBuilder.equalTo(AttributeBuilder.build("enabled", true)))).isTrue();
        assertThat(spec.matches(FilterBuilder.contains(AttributeBuilder.build("enabled", "e")))).isFalse();
    }
}
