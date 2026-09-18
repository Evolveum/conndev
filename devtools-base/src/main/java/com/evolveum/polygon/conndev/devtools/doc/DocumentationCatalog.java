/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.devtools.doc;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;

import java.io.*;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Query view over a parsed {@link DocManifest}.
 *
 * <p>Topics are addressed by their stable {@code key}. Resolution by key first returns the
 * topic whose protocol matches the requested one and then falls back to the protocol-less
 * (generic) topic of the same key, so a panel can render a protocol-specific page on top of
 * the generic one. Topics of the same key under a different protocol are not returned.
 *
 * <p><b>Multiple documentation JARs on the classpath.</b> Each documentation JAR/bundle
 * carries its own {@code docs.yaml} at the well-known location. Use
 * {@link #discover(ClassLoader)} to find and parse every manifest on a classpath (one
 * catalog per JAR, disambiguated by {@link DocManifest#connectorId()}). Such catalogs
 * remember the manifest's origin, so {@link #documentUrl(DocTopic)} and
 * {@link #readDocument(DocTopic)} resolve a topic's HTML <em>within the same JAR</em> the
 * manifest came from — unambiguous even when several JARs package the same relative resource
 * path. Catalogs parsed from plain content (no origin) cannot resolve documents.
 */
public final class DocumentationCatalog {

    private static final YAMLMapper MAPPER = (YAMLMapper) new YAMLMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private final DocManifest manifest;
    private final URL origin;

    private DocumentationCatalog(DocManifest manifest, URL origin) {
        this.manifest = manifest;
        this.origin = origin;
    }

    /**
     * Parses a {@code docs.yaml} manifest from plain content.
     *
     * <p>The resulting catalog has no origin and therefore cannot resolve document URLs.
     *
     * @param yaml the manifest content
     * @return the catalog over the parsed manifest
     * @throws IllegalArgumentException if the content is not a valid manifest
     */
    public static DocumentationCatalog parse(String yaml) {
        try {
            return new DocumentationCatalog(MAPPER.readValue(yaml, DocManifest.class), null);
        } catch (IOException e) {
            throw new IllegalArgumentException("Unparseable documentation manifest", e);
        }
    }

    /**
     * Reads and parses a {@code docs.yaml} manifest from a reader.
     *
     * <p>The resulting catalog has no origin and therefore cannot resolve document URLs.
     *
     * @param reader the reader of the manifest content
     * @return the catalog over the parsed manifest
     * @throws IOException if reading or parsing fails
     */
    public static DocumentationCatalog parse(Reader reader) throws IOException {
        return new DocumentationCatalog(MAPPER.readValue(reader, DocManifest.class), null);
    }

    /**
     * Reads and parses a {@code docs.yaml} manifest from its location, remembering the
     * origin so that topic documents can be resolved within the same JAR/directory.
     *
     * @param manifestUrl the URL of a {@code docs.yaml} manifest
     * @return the origin-aware catalog over the parsed manifest
     * @throws IOException if reading or parsing fails
     */
    public static DocumentationCatalog parse(URL manifestUrl) throws IOException {
        try (var input = manifestUrl.openStream()) {
            var reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8));
            return new DocumentationCatalog(MAPPER.readValue(reader, DocManifest.class), manifestUrl);
        }
    }

    /**
     * Discovers every documentation manifest reachable through the given class loader and
     * parses each into an origin-aware catalog.
     *
     * <p>This is the entry point for the multi-JAR situation: each documentation JAR on the
     * classpath contributes one manifest (and thus one catalog), distinguished by
     * {@link DocManifest#connectorId()}.
     *
     * @param classLoader the class loader whose classpath to scan
     * @return one catalog per manifest found, in classpath order; empty when none is present
     * @throws IOException if reading or parsing any manifest fails
     */
    public static List<DocumentationCatalog> discover(ClassLoader classLoader) throws IOException {
        var catalogs = new ArrayList<DocumentationCatalog>();
        for (URL url : Collections.list(classLoader.getResources(ConndevDocFormat.MANIFEST_RESOURCE))) {
            catalogs.add(parse(url));
        }
        return catalogs;
    }

    /**
     * @return the parsed manifest
     */
    public DocManifest manifest() {
        return manifest;
    }

    /**
     * @return the location the manifest was loaded from, or null when parsed from plain content
     */
    public URL origin() {
        return origin;
    }

    /**
     * @return true when the catalog can resolve topic documents (i.e. it has an origin)
     */
    public boolean hasOrigin() {
        return origin != null;
    }

    /**
     * @return all topics in the manifest, ordered by the manifest's {@code order} field;
     *         topics without an explicit order keep their declaration order relative to
     *         each other and sort after the ordered ones
     */
    public List<DocTopic> allTopics() {
        var topics = new ArrayList<>(manifest.topics());
        topics.sort(Comparator.comparingInt(DocTopic::sortKey));
        return List.copyOf(topics);
    }

    /**
     * Resolves the topics for the given key.
     *
     * <p>Result ordering: first the topics whose protocol equals the requested protocol,
     * then the protocol-less (generic) topics of the same key. A null (or blank) protocol
     * returns the generic topics only.
     *
     * @param key      the topic key
     * @param protocol the protocol to resolve for, or null for the generic topics only
     * @return the matching topics, protocol-specific before generic; empty when the key is
     *         unknown
     */
    public List<DocTopic> topics(String key, String protocol) {
        var requested = (protocol == null || protocol.isBlank()) ? null : protocol;
        var specific = new ArrayList<DocTopic>();
        var generic = new ArrayList<DocTopic>();
        for (DocTopic topic : manifest.topics()) {
            if (!topic.key().equals(key)) {
                continue;
            }
            if (requested != null && requested.equals(topic.protocol())) {
                specific.add(topic);
            } else if (topic.isGeneric()) {
                generic.add(topic);
            }
        }
        specific.sort(Comparator.comparingInt(DocTopic::sortKey));
        generic.sort(Comparator.comparingInt(DocTopic::sortKey));
        var result = new ArrayList<DocTopic>(specific.size() + generic.size());
        result.addAll(specific);
        result.addAll(generic);
        return List.copyOf(result);
    }

    /**
     * Resolves the rendered document of a topic as a sibling of this catalog's manifest,
     * i.e. inside the same JAR/directory the manifest was loaded from.
     *
     * @param topic the topic whose document to resolve
     * @return the URL of the rendered HTML
     * @throws IllegalStateException when the catalog was parsed from plain content (no origin)
     */
    public URL documentUrl(DocTopic topic) {
        if (origin == null) {
            throw new IllegalStateException(
                    "Catalog has no origin; parse the manifest from its URL to resolve documents");
        }
        return ConndevDocFormat.documentUrl(origin, topic.resource());
    }

    /**
     * Reads the rendered document of a topic from the same JAR/directory as this catalog's
     * manifest.
     *
     * @param topic the topic whose document to read
     * @return the document content as a UTF-8 string
     * @throws IOException if reading fails
     * @throws IllegalStateException when the catalog was parsed from plain content (no origin)
     */
    public String readDocument(DocTopic topic) throws IOException {
        var url = documentUrl(topic);
        try (var input = url.openStream()) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /**
     * Opens a stream on the rendered document of a topic (same JAR/directory as the manifest).
     *
     * @param topic the topic whose document to open
     * @return a stream on the rendered HTML; the caller is responsible for closing it
     * @throws IOException if opening fails
     * @throws IllegalStateException when the catalog was parsed from plain content (no origin)
     */
    public InputStream openDocument(DocTopic topic) throws IOException {
        return documentUrl(topic).openStream();
    }
}
