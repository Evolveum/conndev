/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.yaml.model;

/**
 * A reference attribute of a {@link YamlSchemaDocument} — the YAML counterpart of
 * {@code reference("...") { objectClass "..."; role SUBJECT; ... }}.
 */
public class YamlReference {

    /** The referenced object class. */
    public String objectClass;
    /** Role of the holder in the reference: {@code subject} or {@code object}. */
    public String role;
    /** Relationship name grouping both sides (ConnId subtype). */
    public String subtype;

    public String description;
    public Boolean required;
    public Boolean multiValued;
}
