/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.build;

import com.evolveum.polygon.conndev.annotations.Script;
import com.evolveum.polygon.conndev.api.FilterSpecification;
import com.evolveum.polygon.conndev.groovy.api.SearchScriptContext;
import groovy.lang.Closure;
import groovy.lang.DelegatesTo;


public interface SearchScriptBuilder {

    SearchScriptBuilder emptyFilterSupported(boolean emptyFilterSupported);

    SearchScriptBuilder implementation(@DelegatesTo(SearchScriptContext.class) @Script.Runtime Closure<?> implementation);

    SearchScriptBuilder supportedFilter(FilterSpecification filterSpec);

    FilterSpecification.Attribute attribute(String name);


}
