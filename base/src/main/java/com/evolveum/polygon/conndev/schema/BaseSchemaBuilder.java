/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.schema;

import com.evolveum.polygon.conndev.annotations.Script;
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
import org.identityconnectors.framework.common.objects.ObjectClassInfo;
import org.identityconnectors.framework.common.objects.Schema;
import org.identityconnectors.framework.common.objects.Uid;
import org.identityconnectors.framework.spi.Connector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Builder for constructing a {@link BaseSchema} from Groovy connector definitions.
 *
 * <p>Extends {@link SchemaBuilder} with type-safe generics for the object class builder
 * and attribute builder types. Each object class is registered by name and its
 * definition is lazily created via {@link #newObjectClass(DefinitionValue)}.</p>
 *
 * @param <T> the concrete builder type extending this class
 * @param <OB> the object class definition builder type
 * @param <OA> the attribute/object attribute builder type
 * @param <OC> the object class definition type produced by {@code OB.build()}
 * @param <S> the concrete schema type produced by {@link #build()}
 */
public class BaseSchemaBuilder<
        T extends BaseSchemaBuilder<T, OB, SB, OA, OC, S>,
        OB extends BaseObjectClassDefinitionBuilder<OA,OC,?,?,?,?>,
        SB extends SchemaBuilder<SB, OA>,
        OA extends ObjectClassSchemaBuilder<OA,?,?>,
        OC extends BaseObjectClassDefinition<?>,
        S extends BaseSchema<OC>
    > implements SchemaBuilder<SB, OA> {

    /** The connector class for which this schema is being built. */
    protected final Class<? extends Connector> connectorClass;
    /** The registered object class builders, keyed by name. */
    protected final Map<String, OB> objectClasses = new HashMap<>();
    /** Ready-made ConnId object classes added via {@link #defineObjectClass(ObjectClassInfo)}. */
    protected final List<ObjectClassInfo> additionalObjectClasses = new ArrayList<>();
    /** The context lookup for resolving values during schema initialization. */
    private ContextLookup contextLookup;

    /**
     * Constructs a new BaseSchemaBuilder for the given connector class and context.
     *
     * @param connectorClass the connector class for which schema is being built
     * @param context the context lookup for value resolution
     */
    public BaseSchemaBuilder(Class<? extends Connector> connectorClass, ContextLookup context) {
        this.connectorClass = connectorClass;
        this.contextLookup = context;
    }


    /**
     * Returns the object class builder for the given name, creating it lazily if it does not exist.
     *
     * @param name the object class name
     * @return the object class builder for fluent configuration
     */
    @Override
    public OA objectClass(String name) {
        var definitionName = DefinitionValue.from(name, SourceLocation.capture());
        return objectClass(definitionName);
    }

    @Override
    public OA objectClass(DefinitionValue<String> name) {
        return objectClasses.computeIfAbsent(name.value(), k -> newObjectClass(name)).self();
    }

    @Override
    public Optional<OA> lookupObjectClass(Predicate<OA> lookup) {
        return objectClasses.values().stream().map(o -> o.self()).filter(lookup).findFirst();
    }

    /**
     * Creates a new object class builder instance. Override this method to provide a custom
     * object class builder implementation.
     *
     * @param name the object class name
     * @return a new object class builder
     */
    protected OB newObjectClass(DefinitionValue<String> name) {
        return (OB) new BaseObjectClassDefinitionBuilder(BaseSchemaBuilder.this, name);
    }


    /**
     * Defines an object class with the given name and applies the closure to its builder.
     *
     * @param name the object class name
     * @param closure the closure to configure the object class builder
     * @return the object class builder for further configuration
     */
    @Override
    public OA objectClass(
            String name,
            @DelegatesTo(value = BaseObjectClassDefinitionBuilder.class, strategy = Closure.DELEGATE_ONLY)
            @Script.Initialization
            Closure<?> closure) {
        return GroovyClosures.callAndReturnDelegate(closure, objectClass(name));
    }

    /**
     * Defines a relationship with the given name and applies the closure to its builder.
     *
     * <p>Currently returns {@code null} as the relationship implementation is pending.
     * See {@link #relationship(String, Closure)} for future implementation.</p>
     *
     * @param name the relationship name
     * @param closure the closure to configure the relationship builder (unused)
     * @return TODO - relationship builder implementation
     */
    @Override
    public RelationshipBuilder relationship(
            String name,
            @DelegatesTo(value = RelationshipBuilder.class, strategy = Closure.DELEGATE_ONLY)
            Closure<?> closure) {
        // FIXME: Instantiate AbstractRelationshipBuilder once subject/object methods are implemented
        return null;
    }

    /**
     * Adds a ready-made ConnId object class (e.g. the shared conndev dev object classes) to the
     * schema, alongside the ones built from registered object class builders.
     *
     * @param objectClass the ConnId object class info to add
     * @return this builder for fluent chaining
     */
    @SuppressWarnings("unchecked")
    public T defineObjectClass(ObjectClassInfo objectClass) {
        additionalObjectClasses.add(objectClass);
        return (T) this;
    }

    /**
     * Builds the schema from all registered object classes or creates a dummy schema
     * if none have been defined.
     *
     * @return the fully constructed schema
     */
    public S build() {
        if (objectClasses.isEmpty()) {
            initializeDummySchema();
        }

        var freshSchemaBuilder = new org.identityconnectors.framework.common.objects.SchemaBuilder(connectorClass);
        Map<ObjectClass, OC> objectClassMap = new HashMap<>();
        for (var ocBuilder : objectClasses.values()) {
            var objectClassDef = ocBuilder.build();
            freshSchemaBuilder.defineObjectClass(objectClassDef.connId());
            objectClassMap.put(objectClassDef.objectClass(), objectClassDef);
        }
        for (var info : additionalObjectClasses) {
            freshSchemaBuilder.defineObjectClass(info);
            contributeAdditionalObjectClass(info, objectClassMap);
        }
        return newSchema(freshSchemaBuilder.build(), objectClassMap);
    }

    /**
     * Creates the schema instance from the built ConnId schema and object-class map. Default
     * produces a protocol-agnostic {@link BaseSchema}; override to return a connector-specific
     * schema subtype.
     */
    @SuppressWarnings("unchecked")
    protected S newSchema(Schema connIdSchema, Map<ObjectClass, OC> objectClassMap) {
        return (S) new BaseSchema<>(connIdSchema, objectClassMap);
    }

    /**
     * Hook called for each {@link #defineObjectClass(ObjectClassInfo)} entry once it's been added
     * to the ConnId schema, letting a connector represent it in the object-class map too (e.g.
     * wrapped in a mapping-less object class definition). Default: no-op.
     */
    protected void contributeAdditionalObjectClass(ObjectClassInfo info, Map<ObjectClass, OC> objectClassMap) {
        // Default: no-op.
    }

    /**
     * This is workaround for state in connector development (and MidPoint), which prevents issuing test connection
     * without any object class
     */
    protected void initializeDummySchema() {
        var oc = objectClass("__Dummy");
        oc.attribute("id").connId().name(Uid.NAME).type(String.class);
        oc.attribute("name").connId().name(Name.NAME).type(String.class);
    }

    /**
     * Returns all registered object class builders.
     *
     * @return iterable of all OB object class builder instances
     */
    public Iterable<OB> allObjectClasses() {
        return objectClasses.values();
    }

    /**
     * Returns the context lookup used for resolving values during schema building.
     *
     * @return the ContextLookup instance
     */
    public ContextLookup contextLookup() {
        return this.contextLookup;
    }
}
