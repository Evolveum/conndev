/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.yaml.decl;

import com.evolveum.polygon.conndev.concepts.SourceLocation;
import org.identityconnectors.common.security.GuardedByteArray;
import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.framework.common.objects.ConnectorObjectReference;
import org.identityconnectors.framework.common.objects.EmbeddedObject;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.ZonedDateTime;
import java.util.Map;

/**
 * Resolves a ConnId attribute value type name (as written in YAML, e.g. {@code String},
 * {@code GuardedString}) to the corresponding ConnId Java class. The names are the case-sensitive
 * Java simple names (the one for {@code byte[]} is {@code Binary}). This is the
 * {@code @Yaml.ValueParser} target of {@code connId.type}.
 */
public final class DeclConnIdTypeParser implements DeclYamlValueParser {

    /**
     * ConnId attribute value types by their (case-sensitive) Java simple name — the full set from
     * {@code FrameworkUtil.getAllSupportedAttributeTypes()}, including {@code ConnectorObjectReference}
     * and {@code EmbeddedObject}.
     */
    private static final Map<String, Class<?>> CONNID_TYPES = Map.ofEntries(
            Map.entry("String", String.class),
            Map.entry("Integer", Integer.class),
            Map.entry("Long", Long.class),
            Map.entry("Boolean", Boolean.class),
            Map.entry("Double", Double.class),
            Map.entry("Float", Float.class),
            Map.entry("Character", Character.class),
            Map.entry("Byte", Byte.class),
            Map.entry("Binary", byte[].class),
            Map.entry("BigDecimal", BigDecimal.class),
            Map.entry("BigInteger", BigInteger.class),
            Map.entry("GuardedString", GuardedString.class),
            Map.entry("GuardedByteArray", GuardedByteArray.class),
            Map.entry("ZonedDateTime", ZonedDateTime.class),
            Map.entry("Map", Map.class),
            Map.entry("ConnectorObjectReference", ConnectorObjectReference.class),
            Map.entry("EmbeddedObject", EmbeddedObject.class));

    @Override
    public Object coerce(LocatedNode value, SourceLocation location, Class<?> targetType) {
        String name = value.text() == null ? "" : value.text();
        Class<?> type = CONNID_TYPES.get(name);
        if (type == null) {
            throw new IllegalArgumentException("Unknown connId type '" + value.text() + "' at " + location
                    + " (supported: " + String.join(", ", CONNID_TYPES.keySet()) + ")");
        }
        return type;
    }
}
