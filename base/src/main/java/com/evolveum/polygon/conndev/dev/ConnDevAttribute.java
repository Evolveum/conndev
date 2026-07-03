/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.dev;

import org.identityconnectors.framework.common.objects.Attribute;
import org.identityconnectors.framework.common.objects.AttributeBuilder;
import org.identityconnectors.framework.common.objects.EmbeddedObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.evolveum.polygon.conndev.dev.ConnDevObjectClass.*;

/**
 * Fluent builder for a {@code conndev_Attribute} embedded object. Only explicitly-set properties are
 * emitted, so the per-connector mapper decides exactly what each attribute carries. A complex
 * attribute keeps its structure via {@link #subAttribute(String)} (recursive).
 */
public final class ConnDevAttribute {

    private final String name;
    private String type;
    private String namespace;
    private String referencedObjectClass;
    private String referencedAttribute;
    private String reference;
    private String role;
    private Boolean required;
    private Boolean multiValued;
    private Boolean creatable;
    private Boolean updateable;
    private Boolean readable;
    private Boolean returnedByDefault;
    private final List<ConnDevAttribute> subAttributes = new ArrayList<>();

    ConnDevAttribute(String name) {
        this.name = name;
    }

    public ConnDevAttribute type(String type) {
        this.type = type;
        return this;
    }

    public ConnDevAttribute namespace(String namespace) {
        this.namespace = namespace;
        return this;
    }

    public ConnDevAttribute referencedObjectClass(String referencedObjectClass) {
        this.referencedObjectClass = referencedObjectClass;
        return this;
    }

    /** The specific attribute of the referenced object class this attribute points to (e.g. a FK target column). */
    public ConnDevAttribute referencedAttribute(String referencedAttribute) {
        this.referencedAttribute = referencedAttribute;
        return this;
    }

    /** Groups attributes that together form a single reference (e.g. the columns of one composite foreign key). */
    public ConnDevAttribute reference(String reference) {
        this.reference = reference;
        return this;
    }

    /** Role of this object class in the reference (e.g. {@code subject}/{@code object}). */
    public ConnDevAttribute role(String role) {
        this.role = role;
        return this;
    }

    public ConnDevAttribute required(boolean required) {
        this.required = required;
        return this;
    }

    public ConnDevAttribute multiValued(boolean multiValued) {
        this.multiValued = multiValued;
        return this;
    }

    public ConnDevAttribute creatable(boolean creatable) {
        this.creatable = creatable;
        return this;
    }

    public ConnDevAttribute updateable(boolean updateable) {
        this.updateable = updateable;
        return this;
    }

    public ConnDevAttribute readable(boolean readable) {
        this.readable = readable;
        return this;
    }

    public ConnDevAttribute returnedByDefault(boolean returnedByDefault) {
        this.returnedByDefault = returnedByDefault;
        return this;
    }

    public ConnDevAttribute subAttribute(String name) {
        var sub = new ConnDevAttribute(name);
        subAttributes.add(sub);
        return sub;
    }

    EmbeddedObject build() {
        Set<Attribute> properties = new HashSet<>();
        properties.add(AttributeBuilder.build(F_NAME, name));
        if (type != null) {
            properties.add(AttributeBuilder.build(F_TYPE, type));
        }
        if (namespace != null) {
            properties.add(AttributeBuilder.build(F_NAMESPACE, namespace));
        }
        if (referencedObjectClass != null) {
            properties.add(AttributeBuilder.build(F_REFERENCED_OBJECT_CLASS, referencedObjectClass));
        }
        if (referencedAttribute != null) {
            properties.add(AttributeBuilder.build(F_REFERENCED_ATTRIBUTE, referencedAttribute));
        }
        if (reference != null) {
            properties.add(AttributeBuilder.build(F_REFERENCE, reference));
        }
        if (role != null) {
            properties.add(AttributeBuilder.build(F_ROLE, role));
        }
        if (required != null) {
            properties.add(AttributeBuilder.build(F_REQUIRED, required));
        }
        if (multiValued != null) {
            properties.add(AttributeBuilder.build(F_MULTI_VALUED, multiValued));
        }
        if (creatable != null) {
            properties.add(AttributeBuilder.build(F_CREATABLE, creatable));
        }
        if (updateable != null) {
            properties.add(AttributeBuilder.build(F_UPDATEABLE, updateable));
        }
        if (readable != null) {
            properties.add(AttributeBuilder.build(F_READABLE, readable));
        }
        if (returnedByDefault != null) {
            properties.add(AttributeBuilder.build(F_RETURNED_BY_DEFAULT, returnedByDefault));
        }
        if (!subAttributes.isEmpty()) {
            var built = new ArrayList<EmbeddedObject>();
            for (var sub : subAttributes) {
                built.add(sub.build());
            }
            properties.add(AttributeBuilder.build(F_SUB_ATTRIBUTES, built));
        }
        return new EmbeddedObject(ATTRIBUTE, properties);
    }
}
