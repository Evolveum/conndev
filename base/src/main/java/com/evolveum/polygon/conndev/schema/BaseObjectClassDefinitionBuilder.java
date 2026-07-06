/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.schema;

import com.evolveum.polygon.conndev.api.ContextLookup;
import com.evolveum.polygon.conndev.build.api.AttributeBuilder;
import com.evolveum.polygon.conndev.build.api.ObjectClassSchemaBuilder;

import org.identityconnectors.framework.common.objects.*;

import java.util.HashMap;
import java.util.Map;

public abstract class BaseObjectClassDefinitionBuilder<B extends BaseObjectClassDefinitionBuilder<B, AB, AP> , AB extends BaseAttributeBuilder<AB,AP>, AP extends BaseAttributeDefinition> implements ObjectClassSchemaBuilder<B, AB, AP> {

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
    private boolean embedded;
    private String locator;
    private String namespace;

    public BaseObjectClassDefinitionBuilder(BaseSchemaBuilder restSchemaBuilder, String name) {
        this.name = name;
        this.parent = restSchemaBuilder;
        connIdBuilder.setType(name);
    }

    /*
    @Override
    public BaseAttributeBuilder attribute(String name) {
        return nativeAttributes.computeIfAbsent(name, key -> new BaseAttributeBuilder(this, key));
    }

    @Override
    public BaseAttributeBuilder reference(String name) {
        var builder = nativeAttributes.computeIfAbsent(name, key -> {
            var ret = new BaseAttributeBuilder(BaseObjectClassDefinitionBuilder.this, key);
            ret.connIdBuilder.setType(ConnectorObjectReference.class);
            return ret;
        });
        return builder;
    }
    */

    @Override
    public B embedded(boolean embedded) {
        this.embedded = embedded;
        connIdBuilder.setEmbedded(embedded);
        return (B) this;
    }

    /*
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
    */
    @Override
    public B description(String description) {
        this.description = description;
        return (B) this;
    }

    /**
     * Where the object class lives in the remote system: the resource endpoint for REST/SCIM, the
     * table for SQL — semantically the same concept, hence one generic property.
     */
    public BaseObjectClassDefinitionBuilder locator(String locator) {
        this.locator = locator;
        return this;
    }

    /** Namespace of the object class (SCIM schema URN, SQL schema name). */
    public BaseObjectClassDefinitionBuilder namespace(String namespace) {
        this.namespace = namespace;
        return this;
    }


    public String name() {
        return name;
    }



    public B connIdAttribute(String connIdName, String attributeName) {
        var finalName = BUILT_IN_ATTRIBUTES.get(connIdName);
        if (finalName == null) {
            throw new IllegalArgumentException("No such built-in ConnID attribute: " + connIdName);
        }
        var attribute = attribute(attributeName);
        if (attribute == null) {
            throw new IllegalArgumentException("Attribute " + attributeName + " not found");
        }
        attribute.connId().name(finalName);
        return (B) this;
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
        if (description != null) {
            connIdBuilder.setDescription(description);
        }

        var definition = new BaseObjectClassDefinition(connIdBuilder.build(), nativeAttrs, connIdAttrs);
        definition.locator(locator);
        definition.namespace(namespace);
        return definition;
    }

    public String description() {
        return description;
    }

public boolean embedded() {
        return embedded;
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
