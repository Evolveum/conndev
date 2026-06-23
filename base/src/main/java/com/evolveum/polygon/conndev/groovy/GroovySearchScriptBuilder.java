/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.groovy;

import com.evolveum.polygon.conndev.annotations.Script;
import com.evolveum.polygon.conndev.api.FilterSpecification;
import com.evolveum.polygon.conndev.build.SearchScriptBuilder;
import com.evolveum.polygon.conndev.schema.BaseObjectClassDefinition;
import com.evolveum.polygon.conndev.spi.FilterAwareExecuteQueryProcessor;
import groovy.lang.Closure;

import java.util.HashSet;
import java.util.Set;

public class GroovySearchScriptBuilder implements SearchScriptBuilder, FilterAwareSearchProcessorBuilder {

    final ConnectorContext context;
    public BaseObjectClassDefinition objectClass;
    public final Set<FilterSpecification> supportedFilters = new HashSet<>();
    private Boolean emptyFilterSupported;
//    Set<FilterToRequestMapper> filterMappers = new HashSet<>();
    Closure<?> implementationPrototype;
    private boolean enabled = true;

    public GroovySearchScriptBuilder(ConnectorContext context, BaseObjectClassDefinition objectClass) {
        this.context = context;
        this.objectClass = objectClass;
    }

    @Override
    public SearchScriptBuilder emptyFilterSupported(boolean emptyFilterSupported) {
        this.emptyFilterSupported = emptyFilterSupported;
        return this;
    }

    @Override
    public SearchScriptBuilder implementation(@Script.Runtime Closure<?> implementation) {
        this.implementationPrototype = implementation;
        return this;
    }

    @Override
    public SearchScriptBuilder supportedFilter(FilterSpecification filterSpec) {
        supportedFilters.add(filterSpec);
        if (emptyFilterSupported == null) {
            // If empty filter support was not specified explicitly, we assume that it is not supported
            // when adding explicit filtering
            emptyFilterSupported = false;
        }
        return this;
    }

    @Override
    public FilterSpecification.Attribute attribute(String name) {
        var connId = objectClass.attributeFromProtocolName(name).connId();
        if (connId != null) {
            // FIXME: Create deffered search here
            return FilterSpecification.attribute(connId.getName());
        }
        return FilterSpecification.attribute(name);
    }

    @Override
    public boolean emptyFilterSupported() {
        return emptyFilterSupported;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public FilterAwareExecuteQueryProcessor build() {
        return new ScriptedExecuteQueryProcessor(this);
    }

}
