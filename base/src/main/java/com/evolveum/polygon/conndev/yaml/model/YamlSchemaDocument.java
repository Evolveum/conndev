/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.yaml.model;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Typed root of a declarative YAML schema document — the YAML counterpart of one
 * {@code objectClass("...") { ... }} block of the Groovy schema DSL. Jackson binds the recognized
 * keys into typed fields; an unknown key fails fast ({@code FAIL_ON_UNKNOWN_PROPERTIES} is on by
 * default). One file describes exactly one object class; all sections are optional, so a document
 * may carry the native definition, the ConnId overlay ({@code connId}), or both — documents naming
 * the same object class merge.
 */
public class YamlSchemaDocument {

    public String objectClass;
    public String description;
    public Boolean embedded;

    /**
     * ConnId built-in attribute mapping, e.g. {@code UID: id}, {@code NAME: login} — the counterpart
     * of {@code connIdAttribute("UID", "id")}.
     */
    public Map<String, String> connId = new LinkedHashMap<>();

    /** Plain attributes keyed by their native name; insertion order is preserved. */
    public Map<String, YamlAttribute> attributes = new LinkedHashMap<>();

    /** Reference attributes (memberships, foreign keys) keyed by attribute name. */
    public Map<String, YamlReference> references = new LinkedHashMap<>();
}
