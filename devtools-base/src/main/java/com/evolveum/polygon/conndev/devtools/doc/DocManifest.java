/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.devtools.doc;

import java.util.List;

/**
 * The parsed {@code docs.yaml} manifest of one documentation JAR/bundle.
 *
 * @param apiVersion   the manifest format version (see {@link ConndevDocFormat#API_VERSION})
 * @param connectorId  the identifier of the connector or project the docs belong to
 * @param topics       the declared topics, in manifest order
 */
public record DocManifest(String apiVersion, String connectorId, List<DocTopic> topics) {

    /**
     * @return the declared topics, never null
     */
    public List<DocTopic> topics() {
        return topics == null ? List.of() : topics;
    }
}
