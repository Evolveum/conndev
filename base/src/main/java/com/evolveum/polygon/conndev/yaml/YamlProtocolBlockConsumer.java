/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.yaml;

import tools.jackson.databind.JsonNode;

/**
 * Opt-in hook for an object class builder that wants to interpret a protocol-specific top-level
 * block in a YAML schema document (e.g. {@code sql:} for connector-sql, {@code scim:} for
 * connector-scimrest) — the YAML counterpart of the {@code sql()}/{@code scim()} Groovy DSL blocks.
 *
 * <p>{@link YamlSchemaLoader} stays protocol-agnostic: any top-level key it does not recognize
 * itself (not {@code attributes}, {@code connId}, ...) is handed to the current object class
 * builder via {@link #applyProtocolBlock(String, JsonNode)} if it implements this interface;
 * otherwise loading fails fast, exactly like an unrecognized key inside {@code attributes}.
 */
public interface YamlProtocolBlockConsumer {

    /**
     * Applies a protocol-specific block named {@code name} (e.g. {@code "sql"}) to this object
     * class builder. Implementations should reject block names they don't understand by throwing
     * {@link IllegalArgumentException}, and validate their own nested structure (e.g. by converting
     * {@code block} via {@link YamlDocuments#convert(JsonNode, Class)}, which fails fast on unknown
     * nested keys the same way the rest of the YAML schema DSL does).
     *
     * @param name  the block's key in the YAML document
     * @param block the block's raw content
     */
    void applyProtocolBlock(String name, JsonNode block);
}
