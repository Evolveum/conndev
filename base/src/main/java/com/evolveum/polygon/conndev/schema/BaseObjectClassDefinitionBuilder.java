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

import com.evolveum.polygon.conndev.build.api.ReferenceAttributeBuilder;
import com.evolveum.polygon.conndev.build.api.RelationshipBuilder;
import com.evolveum.polygon.conndev.concepts.DefinitionValue;
import com.evolveum.polygon.conndev.concepts.GroovyClosures;
import com.evolveum.polygon.conndev.concepts.SourceLocation;
import groovy.lang.Closure;
import groovy.lang.DelegatesTo;
import org.identityconnectors.framework.common.objects.*;

import java.util.HashMap;
import java.util.Map;

public class BaseObjectClassDefinitionBuilder<OB extends BaseObjectClassDefinitionBuilder<OB, AB, RB, AP> ,
        AB extends BaseAttributeBuilder<AB,AP>,
        RB extends BaseAttributeBuilder<RB,AP>,
        AP extends BaseAttributeDefinition> implements ObjectClassSchemaBuilder<OB, AB, RB> {

    private static final Map<String, String> BUILT_IN_ATTRIBUTES;


    static {
        Map<String, String> builder = new HashMap<>();
        builder.put("UID", Uid.NAME);
        builder.put("NAME", Name.NAME);
        BUILT_IN_ATTRIBUTES = Map.copyOf(builder);

    }

    private final DefinitionValue<String> name;
    private final ObjectClassInfoBuilder connIdBuilder = new ObjectClassInfoBuilder();
    private final BaseSchemaBuilder parent;
    Map<String, BaseAttributeBuilder> nativeAttributes = new HashMap<>();
    private String description;
    private boolean embedded;
    private String locator;
    private String namespace;

    public BaseObjectClassDefinitionBuilder(BaseSchemaBuilder restSchemaBuilder, DefinitionValue<String> name) {
        this.name = name;
        this.parent = restSchemaBuilder;

    }


    @Override
    public AB attribute(String name) {
        var def = DefinitionValue.from(name, SourceLocation.capture());
        return (AB) nativeAttributes.computeIfAbsent(name, key -> new BaseAttributeBuilder(this, def));
    }

    @Override
    public RB reference(String name) {
        var def = DefinitionValue.from(name, SourceLocation.capture());
        var builder = nativeAttributes.computeIfAbsent(name, key -> {
            var ret = new BaseAttributeBuilder(BaseObjectClassDefinitionBuilder.this, def);
            ret.connIdBuilder.type(def.derived(ConnectorObjectReference.class));
            return ret;
        });
        return (RB) builder;
    }

    @Override
    public OB embedded(boolean embedded) {
        this.embedded = embedded;
        connIdBuilder.setEmbedded(embedded);
        return (OB) this;
    }

    @Override
    public AB attribute(String name, @DelegatesTo(AttributeBuilder.class) Closure<?> closure) {
        var attr = attribute(name);
        return GroovyClosures.callAndReturnDelegate(closure, attr);
    }

    @Override
    public RB reference(String name, @DelegatesTo(ReferenceAttributeBuilder.class) Closure<?> closure) {
        var attr = reference(name);
        return GroovyClosures.callAndReturnDelegate(closure, attr);
    }

    @Override
    public OB description(String description) {
        this.description = description;
        return (OB) this;
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
        return name.value();
    }



    public OB connIdAttribute(String connIdName, String attributeName) {
        var finalName = BUILT_IN_ATTRIBUTES.get(connIdName);
        if (finalName == null) {
            throw new IllegalArgumentException("No such built-in ConnID attribute: " + connIdName);
        }
        var attribute = attribute(attributeName);
        if (attribute == null) {
            throw new IllegalArgumentException("Attribute " + attributeName + " not found");
        }
        attribute.connId().name(finalName);
        return (OB) this;
    }


    public BaseObjectClassDefinition build() {
        connIdBuilder.setType(name.value());
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
