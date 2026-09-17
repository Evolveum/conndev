/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.devtools.doc;

import org.testng.annotations.Test;

import java.io.IOException;
import java.io.StringReader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests of the {@link DocumentationCatalog} over synthetic manifest fixtures.
 */
public class DocumentationCatalogTest {

    private static final String MANIFEST = """
            apiVersion: conndev-doc/v1
            connectorId: test-connector
            topics:
              - key: setup
                title: Setup
                resource: scim/setup.html
                order: 20
                protocol: scim
              - key: setup
                title: Setup (generic)
                resource: general/setup.html
                order: 10
              - key: authentication
                title: Authentication (scim)
                resource: scim/authentication.html
                order: 30
                protocol: scim
              - key: authentication
                title: Authentication (rest)
                resource: rest/authentication.html
                order: 40
                protocol: rest
              - key: troubleshooting
                title: Troubleshooting
                resource: general/troubleshooting.html
                order: 50
                group: Support
                description: Errors and fixes
            """;

    @Test
    public void parsesAllFields() {
        var manifest = DocumentationCatalog.parse(MANIFEST).manifest();

        assertThat(manifest.apiVersion()).isEqualTo("conndev-doc/v1");
        assertThat(manifest.connectorId()).isEqualTo("test-connector");
        assertThat(manifest.topics()).hasSize(5);

        var specific = manifest.topics().get(0);
        assertThat(specific.key()).isEqualTo("setup");
        assertThat(specific.protocol()).isEqualTo("scim");
        assertThat(specific.title()).isEqualTo("Setup");
        assertThat(specific.resource()).isEqualTo("scim/setup.html");
        assertThat(specific.order()).isEqualTo(20);
        assertThat(specific.isGeneric()).isFalse();

        assertThat(manifest.topics().get(1).isGeneric()).isTrue();
        assertThat(manifest.topics().get(4).group()).isEqualTo("Support");
        assertThat(manifest.topics().get(4).description()).isEqualTo("Errors and fixes");
        assertThat(manifest.topics().get(4).protocol()).isNull();
    }

    @Test
    public void allTopicsAreSortedByOrder() {
        var yaml = """
                apiVersion: conndev-doc/v1
                connectorId: t
                topics:
                  - key: b
                    title: B
                    resource: b.html
                    order: 20
                  - key: a
                    title: A
                    resource: a.html
                    order: 10
                  - key: c
                    title: C
                    resource: c.html
                """;

        assertThat(DocumentationCatalog.parse(yaml).allTopics())
                .extracting(DocTopic::key)
                .containsExactly("a", "b", "c");
    }

    @Test
    public void topicsByKeyAndProtocolReturnSpecificBeforeGeneric() {
        var catalog = DocumentationCatalog.parse(MANIFEST);

        assertThat(catalog.topics("setup", "scim"))
                .extracting(DocTopic::title)
                .containsExactly("Setup", "Setup (generic)");
    }

    @Test
    public void topicsByKeyAndProtocolExcludeOtherProtocols() {
        var catalog = DocumentationCatalog.parse(MANIFEST);

        assertThat(catalog.topics("authentication", "rest"))
                .extracting(DocTopic::title)
                .containsExactly("Authentication (rest)");
    }

    @Test
    public void topicsByNullProtocolReturnGenericOnly() {
        var catalog = DocumentationCatalog.parse(MANIFEST);

        assertThat(catalog.topics("setup", null))
                .extracting(DocTopic::title)
                .containsExactly("Setup (generic)");
        assertThat(catalog.topics("authentication", null)).isEmpty();
        // blank protocol behaves like null
        assertThat(catalog.topics("setup", " ")).extracting(DocTopic::title)
                .containsExactly("Setup (generic)");
    }

    @Test
    public void unknownKeyYieldsNoTopics() {
        assertThat(DocumentationCatalog.parse(MANIFEST).topics("nope", "scim")).isEmpty();
    }

    @Test
    public void missingTopicsListIsTolerated() {
        var manifest = DocumentationCatalog.parse("apiVersion: conndev-doc/v1\nconnectorId: t\n")
                .manifest();

        assertThat(manifest.topics()).isEmpty();
        assertThat(DocumentationCatalog.parse("apiVersion: conndev-doc/v1").allTopics()).isEmpty();
    }

    @Test
    public void parseFromReader() throws IOException {
        var catalog = DocumentationCatalog.parse(new StringReader(MANIFEST));

        assertThat(catalog.manifest().topics()).hasSize(5);
    }

    @Test
    public void malformedManifestIsRejected() {
        assertThatThrownBy(() -> DocumentationCatalog.parse("topics: [unclosed"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
