/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.schema;

import com.evolveum.polygon.conndev.api.ContextLookup;
import com.evolveum.polygon.conndev.concepts.GroovyClosures;
import com.evolveum.polygon.conndev.build.ObjectClassSchemaBuilder;
import com.evolveum.polygon.conndev.build.AttributeBuilder;
import com.evolveum.polygon.conndev.build.ReferenceAttributeBuilder;
import groovy.lang.Closure;
import groovy.lang.DelegatesTo;
import org.identityconnectors.framework.common.objects.*;

import java.util.HashMap;
import java.util.Map;

public class BaseObjectClassDefinitionBuilder implements ObjectClassSchemaBuilder {

    private static final Map<String, String> BUILT_IN_ATTRIBUTES;


    static {
        Map<String, String> builder = new HashMap<>();
        builder.put("UID", Uid.NAME);
        builder.put("NAME", Name.NAME);
        BUILT_IN_ATTRIBUTES = Map.copyOf(builder);

    }

    private final String name;
    private final ObjectClassInfoBuilder connIdBuilder = new ObjectClassInfoBuilder();
    private final BaseSchemaBuilder parent;
    Map<String, BaseAttributeBuilder> nativeAttributes = new HashMap<>();
    private String description;

    public BaseObjectClassDefinitionBuilder(BaseSchemaBuilder restSchemaBuilder, String name) {
        this.name = name;
        this.parent = restSchemaBuilder;
        connIdBuilder.setType(name);
    }

    @Override
    public BaseAttributeBuilder attribute(String name) {
        return nativeAttributes.computeIfAbsent(name, (k) -> new BaseAttributeBuilder(this, k));
    }

    @Override
    public BaseAttributeBuilder reference(String name) {
        var builder = nativeAttributes.computeIfAbsent(name, (k) -> {
            var ret = new BaseAttributeBuilder(BaseObjectClassDefinitionBuilder.this, k);
            ret.connIdBuilder.setType(ConnectorObjectReference.class);
            return ret;
        });
        return (BaseAttributeBuilder) builder;
    }

    @Override
    public ObjectClassSchemaBuilder embedded(boolean embedded) {
        connIdBuilder.setEmbedded(true);
        return this;
    }

    @Override
    public AbstractAttributeBuilder attribute(String name, @DelegatesTo(AttributeBuilder.class) Closure closure) {
        var attr = attribute(name);
        return GroovyClosures.callAndReturnDelegate(closure, attr);
    }

    @Override
    public BaseAttributeBuilder reference(String name, @DelegatesTo(ReferenceAttributeBuilder.class) Closure closure) {
        var attr = reference(name);
        return GroovyClosures.callAndReturnDelegate(closure, attr);
    }

    @Override
    public BaseObjectClassDefinitionBuilder description(String description) {
        this.description = description;
        return this;
    }


    public String name() {
        return name;
    }



    public BaseObjectClassDefinitionBuilder connIdAttribute(String connIdName, String attributeName) {
        var finalName = BUILT_IN_ATTRIBUTES.get(connIdName);
        if (finalName == null) {
            throw new IllegalArgumentException("No such built-in ConnID attribute: " + connIdName);
        }
        var attribute = attribute(attributeName);
        if (attribute == null) {
            throw new IllegalArgumentException("Attribute " + attributeName + " not found");
        }
        attribute.connId().name(finalName);
        return this;
    }


    public BaseObjectClassDefinition build() {
        var connIdAttrs = new HashMap<String, BaseAttributeDefinition>();
        var nativeAttrs = new HashMap<String, BaseAttributeDefinition>();
        for (var attrBuilder : nativeAttributes.values()) {
            var attribute = attrBuilder.build();
            connIdBuilder.addAttributeInfo(attribute.connId());
            connIdAttrs.put(attribute.connId().getName(), attribute);
            nativeAttrs.put(attribute.remoteName(), attribute);
        }

        return new BaseObjectClassDefinition(connIdBuilder.build(), nativeAttrs, connIdAttrs);
    }

    public String description() {
        return description;
    }

    public boolean embedded() {
        // Embedded means non-manageable object
        return false;
    }

    public Iterable<BaseAttributeBuilder> allAttributes() {
        return nativeAttributes.values();
    }

    public boolean connIdAttributeNotDefined(String name) {
        for (var attrBuilder : nativeAttributes.values()) {
            if (name.equals(attrBuilder.connIdBuilder.build().getName())) {
                return false;
            }
        }
        return true;
    }

    public ContextLookup contextLookup() {
        return parent.contextLookup();
    }

}
