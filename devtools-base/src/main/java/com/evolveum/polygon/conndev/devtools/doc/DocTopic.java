/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.devtools.doc;

/**
 * A single documentation topic declared in the {@code docs.yaml} manifest.
 *
 * <p>The {@code key} is the stable GUI identifier, {@code protocol} tags the topic as
 * protocol-specific (absent or blank for generic topics) and {@code resource} points at the
 * rendered fragment HTML relative to {@link ConndevDocFormat#DOC_ROOT}.
 *
 * @param key         the stable topic identifier used by the GUI
 * @param protocol    the protocol the topic is specific to, or null/blank for generic topics
 * @param title       the display name of the topic
 * @param resource    the rendered HTML resource relative to {@link ConndevDocFormat#DOC_ROOT}
 * @param order       the ordering within the manifest, or null when not declared
 * @param group       the optional sidebar grouping, or null
 * @param description the optional short description, or null
 */
public record DocTopic(String key, String protocol, String title, String resource,
                       Integer order, String group, String description) {

    /**
     * @return true when the topic carries no protocol tag and serves as the generic variant
     */
    public boolean isGeneric() {
        return protocol == null || protocol.isBlank();
    }

    /**
     * @return the manifest order, or {@link Integer#MAX_VALUE} when not declared
     */
    public int sortKey() {
        return order == null ? Integer.MAX_VALUE : order;
    }
}
