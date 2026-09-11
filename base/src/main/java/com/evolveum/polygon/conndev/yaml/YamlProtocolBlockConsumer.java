/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.yaml;

import tools.jackson.databind.JsonNode;

/**
 * Opt-in escape hatch for an object class builder that wants to interpret a protocol-specific
 * top-level block in a YAML schema document that cannot (or does not yet) bind declaratively via
 * {@code @Yaml.*} — the YAML counterpart of a protocol-specific Groovy DSL block. Prefer
 * {@code @Yaml.Sub}/{@code @Yaml.Key}/{@code @Yaml.Custom} on the block's own builder accessor
 * (see {@link com.evolveum.polygon.conndev.annotations.Yaml}) when the block's shape allows it —
 * that keeps the value's YAML source location; this hook only ever sees a plain {@link JsonNode},
 * without location information.
 *
 * <p>{@link YamlSchemaLoader} stays protocol-agnostic: any top-level key it does not recognize
 * itself (not {@code attributes}, {@code connId}, ...) is handed to the current object class
 * builder via {@link #applyProtocolBlock(String, JsonNode)} if it implements this interface;
 * otherwise loading fails fast, exactly like an unrecognized key inside {@code attributes}.
 */
public interface YamlProtocolBlockConsumer {

    /**
     * Applies a protocol-specific block named {@code name} to this object class builder.
     * Implementations should reject block names they don't understand by throwing
     * {@link IllegalArgumentException}, and validate their own nested structure.
     *
     * @param name  the block's key in the YAML document
     * @param block the block's raw content
     */
    void applyProtocolBlock(String name, JsonNode block);
}
