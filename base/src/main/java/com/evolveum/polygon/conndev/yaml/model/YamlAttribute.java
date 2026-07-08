/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.yaml.model;

/**
 * One attribute of a {@link YamlSchemaDocument}. All fields are optional; only explicitly present
 * keys are applied to the builder, so the ConnId defaults stay untouched — exactly like the Groovy
 * DSL, where only invoked methods change the definition.
 */
public class YamlAttribute {

    public String description;

    /** JSON value type ({@code string}, {@code integer}, {@code boolean}, ...). */
    public String jsonType;
    /** OpenAPI format refining the JSON type ({@code int64}, {@code email}, {@code date-time}, ...). */
    public String openApiFormat;
    /**
     * The attribute's name in the serialized/remote form (the JSON field name) when it differs from
     * the attribute name. Maps to {@code json().name()} — the merged successor of the retired
     * {@code protocolName}/{@code remoteName} distinction.
     */
    public String remoteName;

    public Boolean required;
    public Boolean multiValued;
    public Boolean creatable;
    public Boolean updateable;
    public Boolean readable;
    public Boolean returnedByDefault;
    public Boolean emulated;

    /** Marks a structured attribute whose value is the given embedded object class. */
    public String complexType;

    /** Explicit ConnId-side overrides (name/type). */
    public YamlConnIdMapping connId;

    public static class YamlConnIdMapping {
        /** ConnId attribute name override (e.g. {@code __UID__}). */
        public String name;
        /** ConnId value type: {@code string}, {@code integer}, {@code long}, {@code boolean},
         * {@code double}, {@code bigdecimal} or {@code binary}. */
        public String type;
    }
}
