/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.yaml;

import com.evolveum.polygon.conndev.build.api.AttributeBuilder;
import com.evolveum.polygon.conndev.build.api.ReferenceAttributeBuilder;
import com.evolveum.polygon.conndev.schema.BaseObjectClassDefinitionBuilder;
import com.evolveum.polygon.conndev.schema.BaseSchema;
import com.evolveum.polygon.conndev.schema.BaseSchemaBuilder;
import com.evolveum.polygon.conndev.yaml.model.YamlAttribute;
import com.evolveum.polygon.conndev.yaml.model.YamlReference;
import com.evolveum.polygon.conndev.yaml.model.YamlSchemaDocument;
import org.identityconnectors.common.security.GuardedByteArray;
import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.framework.common.objects.AttributeInfo;

import java.io.Reader;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.ZonedDateTime;
import java.util.Locale;
import java.util.Map;

/**
 * Declarative YAML front-end of the schema DSL — the YAML counterpart of
 * {@link com.evolveum.polygon.conndev.groovy.GroovySchemaLoader}. Documents are deserialized by
 * Jackson into the typed {@link YamlSchemaDocument} model (unknown keys fail fast) and applied onto
 * the same {@link BaseSchemaBuilder} the Groovy DSL drives, so both front-ends can be used side by
 * side and a connector can migrate its definitions file by file.
 *
 * <p>One file describes exactly one object class and its type is given by the file-name convention
 * mirroring the Groovy one ({@code User.native.schema.yaml}, {@code User.connid.schema.yaml});
 * multi-document files are rejected. Documents naming the same object class merge into one builder,
 * so the native definition and the ConnId overlay stay in separate files, like in Groovy.
 */
public class YamlSchemaLoader {

    /**
     * ConnId attribute value types by their YAML name — the full set from
     * {@code FrameworkUtil.getAllSupportedAttributeTypes()} except {@code ConnectorObjectReference}
     * and {@code EmbeddedObject}, which are expressed by the dedicated {@code references} and
     * {@code complexType} constructs.
     */
    private static final Map<String, Class<?>> CONNID_TYPES = Map.ofEntries(
            Map.entry("string", String.class),
            Map.entry("integer", Integer.class),
            Map.entry("long", Long.class),
            Map.entry("boolean", Boolean.class),
            Map.entry("double", Double.class),
            Map.entry("float", Float.class),
            Map.entry("character", Character.class),
            Map.entry("byte", Byte.class),
            Map.entry("binary", byte[].class),
            Map.entry("bigdecimal", BigDecimal.class),
            Map.entry("biginteger", BigInteger.class),
            Map.entry("guardedstring", GuardedString.class),
            Map.entry("guardedbytearray", GuardedByteArray.class),
            Map.entry("zoneddatetime", ZonedDateTime.class),
            Map.entry("map", Map.class));

    private final BaseSchemaBuilder schemaBuilder;

    public YamlSchemaLoader(BaseSchemaBuilder schemaBuilder) {
        this.schemaBuilder = schemaBuilder;
    }

    public void load(String yaml) {
        apply(YamlDocuments.readSingle(yaml, YamlSchemaDocument.class, "object class", "inline document"));
    }

    public void load(Reader reader, String sourceName) {
        apply(YamlDocuments.readSingle(reader, YamlSchemaDocument.class, "object class", sourceName));
    }

    public void loadFromResource(String resource) {
        apply(YamlDocuments.readSingleFromResource(getClass(), resource, YamlSchemaDocument.class, "object class"));
    }

    public BaseSchema build() {
        return schemaBuilder.build();
    }

    private void apply(YamlSchemaDocument document) {
        if (document.objectClass == null || document.objectClass.isBlank()) {
            throw new IllegalArgumentException("YAML schema document is missing the objectClass name");
        }

        // The generic schema builder returns the build.api interface; connIdAttribute lives on the
        // concrete builder, so we work with the concrete type the runtime actually produces.
        BaseObjectClassDefinitionBuilder objectClass =
                (BaseObjectClassDefinitionBuilder) schemaBuilder.objectClass(document.objectClass);
        if (document.description != null) {
            objectClass.description(document.description);
        }
        if (Boolean.TRUE.equals(document.embedded)) {
            objectClass.embedded(true);
        }

        document.attributes.forEach((name, attribute) -> apply(objectClass.attribute(name), name, attribute));
        document.references.forEach((name, reference) -> apply(objectClass, name, reference));

        // connIdAttribute validates the built-in alias (UID/NAME) and requires the attribute to exist,
        // so the mapping is applied after the attributes are declared.
        document.connId.forEach(objectClass::connIdAttribute);
    }

    private void apply(AttributeBuilder builder, String name, YamlAttribute attribute) {
        if (attribute == null) {
            return; // "name:" with no keys declares the attribute with defaults
        }
        if (attribute.description != null) {
            builder.description(attribute.description);
        }
        if (attribute.jsonType != null) {
            builder.json().type(attribute.jsonType);
        }
        if (attribute.openApiFormat != null) {
            builder.json().openApiFormat(attribute.openApiFormat);
        }
        if (attribute.remoteName != null) {
            builder.json().name(attribute.remoteName);
        }
        if (attribute.required != null) {
            builder.required(attribute.required);
        }
        if (attribute.multiValued != null) {
            builder.multiValued(attribute.multiValued);
        }
        if (attribute.creatable != null) {
            builder.creatable(attribute.creatable);
        }
        if (attribute.updateable != null) {
            builder.updatable(attribute.updateable);
        }
        if (attribute.readable != null) {
            builder.readable(attribute.readable);
        }
        if (attribute.returnedByDefault != null) {
            builder.returnedByDefault(attribute.returnedByDefault);
        }
        if (attribute.emulated != null) {
            builder.emulated(attribute.emulated);
        }
        if (attribute.complexType != null) {
            builder.complexType(attribute.complexType);
        }
        if (attribute.connId != null) {
            var connId = builder.connId();
            if (attribute.connId.name != null) {
                connId.name(attribute.connId.name);
            }
            if (attribute.connId.type != null) {
                connId.type(connIdType(name, attribute.connId.type));
            }
        }
    }

    private void apply(BaseObjectClassDefinitionBuilder objectClass, String name, YamlReference reference) {
        ReferenceAttributeBuilder builder = objectClass.reference(name);
        if (reference.objectClass == null || reference.objectClass.isBlank()) {
            throw new IllegalArgumentException("Reference '" + name + "' is missing the target objectClass");
        }
        builder.objectClass(reference.objectClass);
        if (reference.role != null) {
            builder.role(roleInReference(name, reference.role));
        }
        if (reference.subtype != null) {
            builder.subtype(reference.subtype);
        }
        if (reference.description != null) {
            builder.description(reference.description);
        }
        if (reference.required != null) {
            builder.required(reference.required);
        }
        if (reference.multiValued != null) {
            builder.multiValued(reference.multiValued);
        }
    }

    private static Class<?> connIdType(String attribute, String type) {
        Class<?> mapped = CONNID_TYPES.get(type.toLowerCase(Locale.ROOT));
        if (mapped == null) {
            throw new IllegalArgumentException("Unknown connId type '" + type + "' of attribute '" + attribute
                    + "' (supported: " + String.join(", ", CONNID_TYPES.keySet()) + ")");
        }
        return mapped;
    }

    private static AttributeInfo.RoleInReference roleInReference(String reference, String role) {
        try {
            return AttributeInfo.RoleInReference.valueOf(role.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown role '" + role + "' of reference '" + reference
                    + "' (supported: subject, object)");
        }
    }
}
