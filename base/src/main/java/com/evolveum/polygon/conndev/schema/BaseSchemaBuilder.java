/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.schema;

import com.evolveum.polygon.conndev.api.ContextLookup;
import com.evolveum.polygon.conndev.concepts.GroovyClosures;
import com.evolveum.polygon.conndev.build.SchemaBuilder;
import com.evolveum.polygon.conndev.build.RelationshipBuilder;
import groovy.lang.Closure;
import groovy.lang.DelegatesTo;
import org.identityconnectors.framework.common.objects.Name;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.Uid;
import org.identityconnectors.framework.spi.Connector;

import java.util.HashMap;
import java.util.Map;

public class BaseSchemaBuilder implements SchemaBuilder {

    private final Class<? extends Connector> connectorClass;
    private final Map<String, BaseObjectClassDefinitionBuilder> objectClasses = new HashMap<>();
    private ContextLookup contextLookup;

    public BaseSchemaBuilder(Class<? extends Connector> connectorClass, ContextLookup context) {
        this.connectorClass = connectorClass;
        this.contextLookup = context;
    }

    @Override
    public BaseObjectClassDefinitionBuilder objectClass(String name) {
        return objectClasses.computeIfAbsent(name, k -> new BaseObjectClassDefinitionBuilder(BaseSchemaBuilder.this, k));
    }

    @Override
    public BaseObjectClassDefinitionBuilder objectClass(String name, @DelegatesTo(BaseObjectClassDefinitionBuilder.class) Closure<?> closure) {
        var objectClass = objectClass(name);
        closure.setDelegate(objectClass);
        closure.setResolveStrategy(Closure.DELEGATE_FIRST);
        closure.call();
        return objectClass;
    }

    @Override
    public RelationshipBuilder relationship(String name, @DelegatesTo(RelationshipBuilder.class) Closure<?> closure) {
        var ret =  new AbstractRelationshipBuilder(name, this);
        return GroovyClosures.callAndReturnDelegate(closure, ret);
    }

    public BaseSchema build() {
        if (objectClasses.isEmpty()) {
            initializeDummySchema();
        }

        var freshSchemaBuilder = new org.identityconnectors.framework.common.objects.SchemaBuilder(connectorClass);
        Map<ObjectClass, BaseObjectClassDefinition> objectClassMap = new HashMap<>();
        for (var ocBuilder : objectClasses.values()) {
            var objectClassDef = ocBuilder.build();
            freshSchemaBuilder.defineObjectClass(objectClassDef.connId());
            objectClassMap.put(objectClassDef.objectClass(), objectClassDef);
        }
        return new BaseSchema(freshSchemaBuilder.build(), objectClassMap);
    }

    /**
     * This is workaround for state in connector development (and MidPoint), which prevents issuing test connection
     * without any object class
     */
    private void initializeDummySchema() {
        var oc = objectClass("__Dummy");
        oc.attribute("id").connId().name(Uid.NAME).type(String.class);
        oc.attribute("name").connId().name(Name.NAME).type(String.class);
    }

    public Iterable<BaseObjectClassDefinitionBuilder> allObjectClasses() {
        return objectClasses.values();
    }

    public ContextLookup contextLookup() {
        return this.contextLookup;
    }
}
