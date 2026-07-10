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
import com.evolveum.polygon.conndev.concepts.DefinitionValue;
import com.evolveum.polygon.conndev.concepts.GroovyClosures;
import com.evolveum.polygon.conndev.concepts.SourceLocation;
import com.evolveum.polygon.conndev.spi.ObjectClassDefinition;
import groovy.lang.Closure;
import groovy.lang.DelegatesTo;
import org.identityconnectors.framework.common.objects.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Base implementation of the object class definition builder.
 *
 * <p>Provides the common building blocks for defining ConnId object classes
 * in connector development scripts: attributes, references, embedded/standalone
 * flag, descriptions, locator and namespace metadata, and ConnId built-in attribute
 * mappings (UID, NAME).
 *
 * <p>Subclasses may override {@link #newAttribute(DefinitionValue)} to supply
 * custom attribute builder implementations while reusing the common attribute
 * storage, lifecycle management, and {@link #build()} logic.
 *
 * @param <B> the concrete object class schema builder type (for self-fluent pattern)
 * @param <A> the concrete attribute builder type
 * @param <R> the concrete reference attribute builder type
 * @param <AB> the concrete attribute builder implementation type (used in {@link #newAttribute})
 * @param <AP> the concrete attribute definition type
 */
public class BaseObjectClassDefinitionBuilder<
        B extends ObjectClassSchemaBuilder<B, A, R> ,
        O extends BaseObjectClassDefinition<AP>,
        A extends AttributeBuilder<? super R,AP>,
        R extends ReferenceAttributeBuilder<R, A, AP>,
        AB extends BaseAttributeBuilder<AB, A, R,  AP>,
        AP extends BaseAttributeDefinition> implements ObjectClassSchemaBuilder<B, A, R> {

    /**
     * Maps ConnId built-in attribute display names to their canonical ConnId names.
     *
     * <p>Supported mappings:
     * <ul>
     *   <li>{@code "UID"} → {@link Uid#NAME} (usually "UID")</li>
     *   <li>{@code "NAME"} → {@link Name#NAME} (usually "NAME")</li>
     * </ul>
     *
     * The map is immutable and populated at class-load time from the ConnId constant definitions.
     */
    private static final Map<String, String> BUILT_IN_ATTRIBUTES;


    static {
        Map<String, String> builder = new HashMap<>();
        builder.put("UID", Uid.NAME);
        builder.put("NAME", Name.NAME);
        BUILT_IN_ATTRIBUTES = Map.copyOf(builder);

    }

    /**
     * Returns this builder instance for fluent method chaining.
     * Implements the {@link ObjectClassSchemaBuilder#self()} contract from {@link Fluent}.
     *
     * @return this builder cast to the generic return type
     */
    @SuppressWarnings("unchecked")
    @Override
    public B self() {
        return (B) this;
    }

    /**
     * The canonical name (display name) of this object class in the DSL.
     */
    private final DefinitionValue<String> name;

    /**
     * ConnId ObjectClassInfo builder used to produce the ConnId {@link ObjectClass} definition.
     */
    private final ObjectClassInfoBuilder connIdBuilder = new ObjectClassInfoBuilder();

    /**
     * The parent schema builder that owns this object class definition.
     */
    private final BaseSchemaBuilder parent;

    /**
     * Map of native (protocol-level) attribute builders keyed by their JSON/remote name.
     */
    Map<String, AB> nativeAttributes = new HashMap<>();

    /**
     * Human-readable description of the object class.
     */
    private String description;

    /**
     * Whether this object class is embedded (returned inline rather than by reference).
     *
     * @see ObjectClassSchemaBuilder#embedded(boolean)
     */
    private boolean embedded;

    /**
     * Locator identifying where this object class lives in the remote system,
     * such as a resource endpoint for REST/SCIM or a table name for SQL.
     */
    private String locator;

    /**
     * Namespace for the object class, such as a SCIM schema URN or SQL schema name.
     */
    private String namespace;

    /**
     * Constructs a new object class definition builder.
     *
     * @param restSchemaBuilder the parent schema builder that owns this object class
     * @param name              the display name of the object class
     */
    public BaseObjectClassDefinitionBuilder(BaseSchemaBuilder restSchemaBuilder, DefinitionValue<String> name) {
        this.name = name;
        this.parent = restSchemaBuilder;
    }


    /**
     * Creates or retrieves an attribute builder by the given name.
     *
     * <p>Delegates to {@link #reference(DefinitionValue)} with a captured source location,
     * then casts the result to the attribute type. The returned {@link AttributeBuilder}
     * will have a ConnId reference type with type {@code ConnectorObjectReference}.
     *
     * @param name the name of the attribute to create or retrieve
     * @return the attribute builder for further configuration
     * @see ObjectClassSchemaBuilder#attribute(String)
     */
    @Override
    public A attribute(String name) {
        return reference(DefinitionValue.from(name, SourceLocation.capture())).asAttribute();
    }

    /**
     * Creates or retrieves a reference builder by the given definition value.
     *
     * <p>If a reference with the given name already exists, the existing builder
     * is returned. Otherwise, a new builder is created via {@link #newAttribute(DefinitionValue)}
     * and stored in the native attributes map.
     *
     * @param name the definition value containing the reference name and source location
     * @return the reference builder for further configuration
     */
    public R reference(DefinitionValue<String> name) {
        return nativeAttributes.computeIfAbsent(name.value(), key -> newAttribute(name)).self();
    }

    /**
     * Factory method that creates a new attribute builder for the given definition.
     *
     * <p>Override this method in subclasses to supply a custom attribute builder
     * implementation (e.g. adding extra configuration capabilities).
     *
     * @param def the definition value containing the attribute name and source location
     * @return a new attribute builder instance
     */
    // TODO: Should be abstract in future when all subclasses use a common base
    protected AB newAttribute(DefinitionValue<String> def) {
        return (AB) new BaseAttributeBuilder(this, def);
    }

    /**
     * Creates or retrieves a reference builder by name, and automatically sets
     * the ConnId type to {@code ConnectorObjectReference}.
     *
     * @param name the name of the reference attribute
     * @return the reference builder for further configuration
     * @see ObjectClassSchemaBuilder#reference(String)
     */
    @Override
    public R reference(String name) {
        var ref = reference(DefinitionValue.from(name, SourceLocation.capture()));
        ref.connId().type(DefinitionValue.detected(ConnectorObjectReference.class));
        return ref;
    }

    /**
     * Marks the object class as embedded (returned inline rather than by reference).
     *
     * @param embedded true if the object class is embedded, false if it is referenced
     * @return this builder for chaining
     * @see ObjectClassSchemaBuilder#embedded(boolean)
     */
    @Override
    public B embedded(boolean embedded) {
        this.embedded = embedded;
        connIdBuilder.setEmbedded(embedded);
        return self();
    }

    /**
     * Creates or retrieves an attribute builder by name and configures it using the given closure.
     *
     * <p>The closure is invoked with the attribute builder as its delegate.
     *
     * @param name    the name of the attribute to create or retrieve
     * @param closure a closure that configures the attribute builder; delegates to {@link AttributeBuilder}
     * @return the attribute builder after closure execution
     * @see ObjectClassSchemaBuilder#attribute(String, Closure)
     */
    @Override
    public A attribute(String name, @DelegatesTo(AttributeBuilder.class) Closure<?> closure) {
        var attr = attribute(name);
        return GroovyClosures.callAndReturnDelegate(closure, attr);
    }

    /**
     * Creates or retrieves a reference builder by name and configures it using the given closure.
     *
     * <p>The closure is invoked with the reference builder as its delegate.
     *
     * @param name    the name of the reference attribute to create or retrieve
     * @param closure a closure that configures the reference builder; delegates to {@link ReferenceAttributeBuilder}
     * @return the reference builder after closure execution
     * @see ObjectClassSchemaBuilder#reference(String, Closure)
     */
    @Override
    public R reference(String name, @DelegatesTo(ReferenceAttributeBuilder.class) Closure<?> closure) {
        var attr = reference(name);
        return GroovyClosures.callAndReturnDelegate(closure, attr);
    }

    /**
     * Sets a human-readable description for this object class.
     *
     * @param description the object class description
     * @return this builder for chaining
     * @see ObjectClassSchemaBuilder#description(String)
     */
    @Override
    public B description(String description) {
        this.description = description;
        return self();
    }

    /**
     * Sets the locator for this object class — where it lives in the remote system.
     *
     * <p>Semantically the same concept across connector types: the resource endpoint
     * for REST/SCIM connectors, the table name for SQL connectors, etc.
     *
     * @param locator the locator string (e.g. a URL endpoint or table name)
     * @return this builder for chaining
     */
    public BaseObjectClassDefinitionBuilder locator(String locator) {
        this.locator = locator;
        return this;
    }

    /**
     * Sets the namespace for this object class.
     *
     * <p>For SCIM connectors this is typically a SCIM schema URN; for SQL connectors
     * this is typically a database schema name.
     *
     * @param namespace the namespace string
     * @return this builder for chaining
     */
    public BaseObjectClassDefinitionBuilder namespace(String namespace) {
        this.namespace = namespace;
        return this;
    }


    /**
     * Returns the display name value of this object class.
     *
     * @return the name as a String
     */
    public String name() {
        return name.value();
    }


    /**
     * Maps a ConnId built-in attribute name (such as "UID" or "NAME") to a protocol-level
     * attribute name defined in this object class.
     *
     * <p>Only "UID" and "NAME" are supported as ConnId built-in attribute names.
     * The method verifies that the target protocol attribute exists in this object class's
     * attribute definitions; if it does not, an {@link IllegalArgumentException} is thrown.
     *
     * @param connIdName    the ConnId built-in attribute name (must be "UID" or "NAME")
     * @param attributeName the protocol (JSON/database column) attribute name to bind it to
     * @return this builder for chaining
     * @throws IllegalArgumentException if connIdName is not a supported built-in attribute,
     *                                  or if the specified attributeName does not exist
     * @see ObjectClassSchemaBuilder#connIdAttribute(String, String)
     */
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
        return self();
    }


    /**
     * Builds and returns the complete {@link BaseObjectClassDefinition}.
     *
     * <p>This method:
     * <ol>
     *   <li>Sets the ConnId object class type to the builder's name</li>
     *   <li>Builds each attribute and adds ConnId attribute info</li>
     *   <li>Sets the description if one was provided</li>
     *   <li>Applies the locator and namespace to the resulting definition</li>
     * </ol>
     *
     * @return the fully built object class definition
     */
    public O build() {
        connIdBuilder.setType(name.value());
        var connIdAttrs = new HashMap<String, AP>();
        var nativeAttrs = new HashMap<String, AP>();
        for (var attrBuilder : nativeAttributes.values()) {
            var attribute = attrBuilder.build();
            connIdBuilder.addAttributeInfo(attribute.connId());
            connIdAttrs.put(attribute.connId().getName(), attribute);
            nativeAttrs.put(attribute.remoteName(), attribute);
        }
        if (description != null) {
            connIdBuilder.setDescription(description);
        }

        var definition = buildImpl(connIdBuilder.build(), nativeAttrs, connIdAttrs);
        definition.locator(locator);
        definition.namespace(namespace);
        return definition;
    }

    protected O buildImpl(ObjectClassInfo build, Map<String, AP> nativeAttrs, Map<String, AP> connIdAttrs) {
        return (O) new BaseObjectClassDefinition<AP>(build, nativeAttrs, connIdAttrs);
    }

    /**
     * Returns the description of this object class.
     *
     * @return the description, or null if none was set
     * @see #description(String)
     */
    public String description() {
        return description;
    }

    /**
     * Returns whether this object class is marked as embedded.
     *
     * @return true if the object class is embedded (returned inline), false otherwise
     * @see #embedded(boolean)
     */
    public boolean embedded() {
        return embedded;
    }

    /**
     * Returns an iterable over all native attribute builders defined in this object class.
     *
     * @return all attribute builders keyed by their remote (JSON) names
     */
    public Iterable<AB> allAttributes() {
        return nativeAttributes.values();
    }

    /**
     * Checks whether no attribute has been mapped to the given ConnId attribute name.
     *
     * <p>Iterates over all attribute builders and checks whether any of them has
     * a ConnId attribute name matching the given value.
     *
     * @param name the ConnId attribute name to check
     * @return true if no attribute is mapped to the given ConnId name, false otherwise
     */
    public boolean connIdAttributeNotDefined(String name) {
        for (var attrBuilder : nativeAttributes.values()) {
            if (name.equals(attrBuilder.connIdBuilder.build().getName())) {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns the {@link ContextLookup} provided by the parent schema builder.
     *
     * @return the context lookup for this object class builder
     */
    public ContextLookup contextLookup() {
        return parent.contextLookup();
    }

}