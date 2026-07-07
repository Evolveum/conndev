/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.schema;

import com.evolveum.polygon.conndev.api.ContextLookup;
import com.evolveum.polygon.conndev.build.api.ObjectClassSchemaBuilder;
import com.evolveum.polygon.conndev.build.api.RelationshipBuilder;
import com.evolveum.polygon.conndev.build.api.SchemaBuilder;
import com.evolveum.polygon.conndev.concepts.DefinitionValue;
import com.evolveum.polygon.conndev.concepts.GroovyClosures;
import com.evolveum.polygon.conndev.concepts.SourceLocation;
import groovy.lang.Closure;
import groovy.lang.DelegatesTo;
import org.identityconnectors.framework.common.objects.Name;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.Uid;
import org.identityconnectors.framework.spi.Connector;

import java.util.HashMap;
import java.util.Map;

public class BaseSchemaBuilder<SB extends BaseSchemaBuilder<SB, OB, OA>,
        OB extends BaseObjectClassDefinitionBuilder<OA,?,?,?,?>,
        OA extends ObjectClassSchemaBuilder<OA,?,?>> implements SchemaBuilder<SB, OA> {

    private final Class<? extends Connector> connectorClass;
    private final Map<String, OB> objectClasses = new HashMap<>();
    private ContextLookup contextLookup;

    public BaseSchemaBuilder(Class<? extends Connector> connectorClass, ContextLookup context) {
        this.connectorClass = connectorClass;
        this.contextLookup = context;
    }


    @Override
    public OA objectClass(String name) {
        var definitionName = DefinitionValue.from(name, SourceLocation.capture());
        return objectClasses.computeIfAbsent(name, k -> newObjectClass(definitionName)).self();
    }

    protected OB newObjectClass(DefinitionValue<String> name) {
        return (OB) new BaseObjectClassDefinitionBuilder(BaseSchemaBuilder.this, name);
    }


    @Override
    public OA objectClass(String name, @DelegatesTo(BaseObjectClassDefinitionBuilder.class) Closure<?> closure) {
        return GroovyClosures.callAndReturnDelegate(closure, objectClass(name));
    }

    @Override
    public RelationshipBuilder relationship(String name, @DelegatesTo(RelationshipBuilder.class) Closure<?> closure) {
        /**
        var ret =  new AbstractRelationshipBuilder(name, this);
        return GroovyClosures.callAndReturnDelegate(closure, ret);
         **/
        // FIXME
        return null;
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

    public Iterable<OB> allObjectClasses() {
        return objectClasses.values();
    }

    public ContextLookup contextLookup() {
        return this.contextLookup;
    }
}
