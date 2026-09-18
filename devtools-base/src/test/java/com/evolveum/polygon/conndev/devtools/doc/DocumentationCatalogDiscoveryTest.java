/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.devtools.doc;

import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests that {@link DocumentationCatalog} copes with several documentation JARs on the
 * classpath, including JARs that package the <em>same</em> relative resource path.
 */
public class DocumentationCatalogDiscoveryTest {

    private static final String MANIFEST_RESOURCE = "META-INF/conndev-doc/docs.yaml";
    private static final String DOC_RESOURCE = "META-INF/conndev-doc/shared/topic.html";

    private Path jarAlpha;
    private Path jarBeta;

    @BeforeClass
    void buildJars() throws IOException {
        jarAlpha = writeJar("alpha", "alpha", "<p>FROM-ALPHA</p>");
        jarBeta = writeJar("beta", "beta", "<p>FROM-BETA</p>");
    }

    @AfterClass
    void cleanUp() throws IOException {
        Files.deleteIfExists(jarAlpha);
        Files.deleteIfExists(jarBeta);
    }

    @Test
    public void discoversOneCatalogPerJar() throws IOException {
        var classLoader = classLoaderOver(jarAlpha, jarBeta);

        var catalogs = DocumentationCatalog.discover(classLoader);

        assertThat(catalogs).hasSize(2);
        assertThat(catalogs).allSatisfy(DocumentationCatalog::hasOrigin);
        assertThat(catalogs)
                .extracting(c -> c.manifest().connectorId())
                .containsExactlyInAnyOrder("alpha", "beta");
    }

    @Test
    public void resolvesEachDocumentWithinItsOwnJarDespiteCollidingPaths() throws IOException {
        var classLoader = classLoaderOver(jarAlpha, jarBeta);
        var catalogs = DocumentationCatalog.discover(classLoader);
        var alpha = byConnectorId(catalogs, "alpha");
        var beta = byConnectorId(catalogs, "beta");

        // Both JARs expose the identical relative path shared/topic.html.
        var alphaTopic = alpha.topics("shared", null).getFirst();
        var betaTopic = beta.topics("shared", null).getFirst();
        assertThat(alphaTopic.resource()).isEqualTo(betaTopic.resource());

        // Each catalog resolves its document from its own JAR, not the first on the classpath.
        assertThat(alpha.readDocument(alphaTopic)).contains("FROM-ALPHA");
        assertThat(beta.readDocument(betaTopic)).contains("FROM-BETA");
        assertThat(alpha.documentUrl(alphaTopic).toExternalForm()).contains(jarAlpha.getFileName().toString());
        assertThat(beta.documentUrl(betaTopic).toExternalForm()).contains(jarBeta.getFileName().toString());
    }

    @Test
    public void discoverOverEmptyClasspathYieldsNoCatalogs() throws IOException {
        var classLoader = classLoaderOver();

        assertThat(DocumentationCatalog.discover(classLoader)).isEmpty();
    }

    @Test
    public void documentResolutionRequiresAnOrigin() {
        var catalog = DocumentationCatalog.parse(
                "apiVersion: conndev-doc/v1\nconnectorId: t\ntopics:\n  - key: k\n    resource: a.html\n");

        assertThat(catalog.hasOrigin()).isFalse();
        assertThat(catalog.origin()).isNull();
        var topic = catalog.topics("k", null).getFirst();
        assertThatThrownBy(() -> catalog.documentUrl(topic)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> catalog.readDocument(topic)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> catalog.openDocument(topic)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    public void documentUrlRejectsNonManifestLocations() throws MalformedURLException {
        assertThatThrownBy(() -> ConndevDocFormat.documentUrl(URI.create("https://example.com/x.html").toURL(), "a.html"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // --- helpers -------------------------------------------------------------

    private static DocumentationCatalog byConnectorId(List<DocumentationCatalog> catalogs, String connectorId) {
        return catalogs.stream()
                .filter(c -> connectorId.equals(c.manifest().connectorId()))
                .findFirst()
                .orElseThrow();
    }

    private static URLClassLoader classLoaderOver(Path... jars) throws MalformedURLException {
        var urls = new ArrayList<URL>();
        for (Path jar : jars) {
            urls.add(jar.toUri().toURL());
        }
        return new URLClassLoader(urls.toArray(new URL[0]), null);
    }

    /** Builds a docs JAR whose manifest and HTML share the same relative resource path. */
    private static Path writeJar(String connectorId, String id, String html) throws IOException {
        var manifest = """
                apiVersion: conndev-doc/v1
                connectorId: %s
                topics:
                  - key: shared
                    title: Shared
                    resource: shared/topic.html
                    order: 10
                """.formatted(connectorId);

        var jar = Files.createTempFile("conndev-docs-" + id + "-", ".jar");
        try (var out = new JarOutputStream(Files.newOutputStream(jar))) {
            put(out, MANIFEST_RESOURCE, manifest);
            put(out, DOC_RESOURCE, html);
        }
        return jar;
    }

    private static void put(JarOutputStream out, String name, String content) throws IOException {
        out.putNextEntry(new JarEntry(name));
        out.write(content.getBytes(StandardCharsets.UTF_8));
        out.closeEntry();
    }
}
