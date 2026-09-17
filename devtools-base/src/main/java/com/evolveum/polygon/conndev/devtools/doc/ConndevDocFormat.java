/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.devtools.doc;

import java.net.MalformedURLException;
import java.net.URL;

/**
 * Constants defining the conndev connector documentation format (the wire contract between
 * the docs build and the documentation panel).
 *
 * <p>A documentation JAR/bundle carries a single manifest at the well-known location
 * {@link #MANIFEST_RESOURCE} and the rendered fragment HTML files under
 * {@link #DOC_ROOT}. The manifest maps GUI topic keys to the HTML resources:
 *
 * <pre>{@code
 * apiVersion: conndev-doc/v1
 * connectorId: scimrest
 * topics:
 *   - key: authentication
 *     title: Authentication
 *     resource: scim/authentication.html
 *     order: 20
 *     protocol: scim
 * }</pre>
 *
 * <p>Topics are addressed by their stable {@code key}. The optional {@code protocol} tag
 * marks a topic as protocol-specific; a key without a protocol is the generic variant and
 * is served after the protocol-specific topics of the same key.
 */
public final class ConndevDocFormat {

    /** Well-known classpath location of the documentation manifest inside a JAR/bundle. */
    public static final String MANIFEST_RESOURCE = "META-INF/conndev-doc/docs.yaml";

    /** File name of the manifest (the last path segment of {@link #MANIFEST_RESOURCE}). */
    public static final String MANIFEST_FILE = "docs.yaml";

    /** Classpath directory the rendered fragment HTML files are packaged under. */
    public static final String DOC_ROOT = "META-INF/conndev-doc/";

    /** Manifest format version. */
    public static final String API_VERSION = "conndev-doc/v1";

    private ConndevDocFormat() {
    }

    /**
     * Resolves a topic's HTML resource as a sibling of the manifest, i.e. inside the same
     * JAR/directory the manifest was loaded from.
     *
     * <p>This keeps document resolution unambiguous when several documentation JARs are on
     * the classpath: every manifest resolves its documents within its own JAR instead of
     * through the (first-wins) classpath.
     *
     * @param manifest the location of a loaded {@code docs.yaml} manifest
     * @param resource the topic resource relative to {@link #DOC_ROOT}
     * @return the URL of the rendered document
     * @throws IllegalArgumentException if the given URL is not a docs manifest location
     */
    public static URL documentUrl(URL manifest, String resource) {
        var location = manifest.toExternalForm();
        if (!location.endsWith(MANIFEST_FILE)) {
            throw new IllegalArgumentException("Not a documentation manifest URL: " + location);
        }
        var base = location.substring(0, location.length() - MANIFEST_FILE.length());
        try {
            return new URL(base + resource);
        } catch (MalformedURLException e) {
            // unreachable: the location is a valid manifest URL, only its file name changed
            throw new IllegalArgumentException("Unresolvable document location: " + base + resource, e);
        }
    }
}
