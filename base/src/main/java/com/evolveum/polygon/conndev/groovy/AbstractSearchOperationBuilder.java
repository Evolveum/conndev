/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.groovy;

import com.evolveum.polygon.conndev.build.api.NormalizationBuilder;
import com.evolveum.polygon.conndev.build.api.SearchOperationBuilder;
import com.evolveum.polygon.conndev.build.api.SearchScriptBuilder;
import com.evolveum.polygon.conndev.schema.BaseAttributeDefinition;
import com.evolveum.polygon.conndev.spi.AttributeResolver;
import com.evolveum.polygon.conndev.spi.AttributeResolvingSearchHandler;
import com.evolveum.polygon.conndev.spi.FilterAwareExecuteQueryProcessor;
import com.evolveum.polygon.conndev.spi.FilterBasedSearchDispatcher;
import com.evolveum.polygon.conndev.spi.ObjectSearchOperation;

import java.util.HashSet;
import java.util.Set;

public abstract class AbstractSearchOperationBuilder implements SearchOperationBuilder {

    private final BaseObjectOperationSupportBuilder<?,?,?,?> parent;
    protected Set<FilterAwareSearchProcessorBuilder> builders = new HashSet<>();
    protected Set<ScriptedAttributeResolverBuilder> resolvers = new HashSet<>();
    private NormalizationBuilderImpl normalizationBuilder;

    protected AbstractSearchOperationBuilder(BaseObjectOperationSupportBuilder<?,?,?,?> parent) {
        this.parent = parent;
    }

    @Override
    public ScriptedAttributeResolverBuilder attributeResolver() {
        var ret = new ScriptedAttributeResolverBuilder(parent.context, parent.getObjectClass());
        resolvers.add(ret);
        return ret;
    }

    @Override
    public SearchScriptBuilder custom() {
        var ret = new GroovySearchScriptBuilder(parent.context, parent.getObjectClass());
        builders.add(ret);
        return ret;
    }

    @Override
    public NormalizationBuilder normalize() {
        if (normalizationBuilder == null) {
            normalizationBuilder = new NormalizationBuilderImpl();
        }
        return normalizationBuilder;
    }

    public ObjectSearchOperation build() {
        if (isEmpty()) {
            // We don't have any endpoints, so we don't need to build anything, this results in search operation
            // being unsupported.
            return null;
        }

        return buildAttributeResolver(buildNormalizationHandler(buildFilterDispatcher()));
    }

    protected boolean isEmpty() {
        return builders.isEmpty();
    }

    private ObjectSearchOperation buildNormalizationHandler(ObjectSearchOperation executeQueryProcessor) {
        if (normalizationBuilder == null) {
            return executeQueryProcessor;
        }
        return normalizationBuilder.build(executeQueryProcessor);
    }

    private ObjectSearchOperation buildFilterDispatcher() {
        var handlers = new HashSet<FilterAwareExecuteQueryProcessor>();
        ObjectSearchOperation emptyFilterHandler = null;
        ObjectSearchOperation anyFilterHandler = null;
        for (var builder : builders) {
            if (builder.isEnabled()) {
                var handler = builder.build();
                handlers.add(handler);
                if (builder.emptyFilterSupported()) {
                    if (emptyFilterHandler == null) {
                        emptyFilterHandler = handler;
                    } else {
                        // FIXME: Throw better exception
                        throw new IllegalStateException("Multiple default endpoints are not supported");
                    }
                }
            }
        }
        return new FilterBasedSearchDispatcher<>(emptyFilterHandler, anyFilterHandler,  handlers);
    }

    private ObjectSearchOperation buildAttributeResolver(ObjectSearchOperation dispatcher) {
        Set<AttributeResolver> perObjectResolvers = new HashSet<>();
        Set<AttributeResolver> batchedResolvers = new HashSet<>();
        Set<BaseAttributeDefinition> supportedAttributes = new HashSet<>();
        for (var builder : resolvers) {
            var resolver = builder.build();
            supportedAttributes.addAll(resolver.getSupportedAttributes());
            switch (builder.resolutionType()) {
                case BATCH -> batchedResolvers.add(resolver);
                case PER_OBJECT -> perObjectResolvers.add(resolver);
                default -> throw new IllegalStateException("Unknown resolver type: " + builder.resolutionType());
            }
        }
        for (var attribute : parent.getObjectClass().attributes()) {
            if (attribute.emulated()) {
                var resolver = attribute.resolver();
                if (resolver == null && !supportedAttributes.contains(attribute)) {
                    throw new IllegalStateException("Attribute: " + attribute.remoteName() + " is emulated, but no resolver exists.");
                }
                switch (resolver.resolutionType()) {
                    case BATCH -> batchedResolvers.add(resolver);
                    case PER_OBJECT -> perObjectResolvers.add(resolver);
                    default -> throw new IllegalStateException("Unknown resolver type: " + resolver.resolutionType());
                }
            }
        }

        if (!perObjectResolvers.isEmpty() || !batchedResolvers.isEmpty()) {
            dispatcher = new AttributeResolvingSearchHandler(dispatcher, perObjectResolvers, batchedResolvers);
        }

        return dispatcher;
    }
}
