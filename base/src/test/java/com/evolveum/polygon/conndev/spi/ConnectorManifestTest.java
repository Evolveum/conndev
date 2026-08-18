/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.spi;

import org.identityconnectors.framework.common.exceptions.ConfigurationException;
import org.testng.annotations.Test;

import java.util.List;

import static org.testng.Assert.expectThrows;
import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertTrue;

/**
 * Resolution and parsing of the connector manifest: the manifest may be written in YAML
 * ({@code connector.manifest.yaml}/{@code .yml}) or JSON ({@code connector.manifest.json}) with the
 * same structure; exactly one format may be bundled; a missing manifest keeps yielding the empty one.
 */
public class ConnectorManifestTest {

    @Test
    public void yamlManifestIsLoaded() {
        var manifest = ConnectorManifest.load(getClass(), "/manifests/yaml/connector.manifest");

        assertEquals(List.of("/User.native.schema.groovy", "/User.connid.schema.groovy"), manifest.schemaScripts());
        assertEquals(List.of("/authorization.op.yaml"), manifest.authorizationScripts());
        assertEquals(List.of("/User.search.all.op.yaml"), manifest.operationScripts());
    }

    @Test
    public void jsonManifestKeepsLoading() {
        var manifest = ConnectorManifest.load(getClass(), "/manifests/json/connector.manifest");

        assertEquals(List.of("/User.native.schema.groovy", "/User.connid.schema.groovy"), manifest.schemaScripts());
        assertEquals(List.of("/authorization.op.yaml"), manifest.authorizationScripts());
        assertEquals(List.of("/User.search.all.op.yaml"), manifest.operationScripts());
    }

    /**
     * Used while validating a not-yet-saved replacement for one script: every other deployed
     * script is reloaded, but not the one being replaced (its candidate content stands in for it
     * instead).
     */
    @Test
    public void excludedSchemaScriptIsOmitted() {
        var manifest = ConnectorManifest.load(getClass(), "/manifests/json/connector.manifest");

        assertEquals(List.of("/User.connid.schema.groovy"), manifest.schemaScripts("/User.native.schema.groovy"));
    }

    /** An excluded resource that isn't actually present changes nothing — e.g. a brand new, not-yet-saved script. */
    @Test
    public void excludingAnUnknownResourceChangesNothing() {
        var manifest = ConnectorManifest.load(getClass(), "/manifests/json/connector.manifest");

        assertEquals(List.of("/User.native.schema.groovy", "/User.connid.schema.groovy"),
                manifest.schemaScripts("/NotDeployed.schema.groovy"));
    }

    @Test
    public void excludedOperationScriptIsOmitted() {
        var manifest = ConnectorManifest.load(getClass(), "/manifests/json/connector.manifest");

        assertEquals(List.of(), manifest.operationScripts("/User.search.all.op.yaml"));
    }

    /** A missing manifest loads as the empty one; reading scripts from it keeps failing like before. */
    @Test
    public void missingManifestYieldsEmptyManifest() {
        var manifest = ConnectorManifest.load(getClass(), "/manifests/missing/connector.manifest");

        var exception = expectThrows(IllegalArgumentException.class, manifest::schemaScripts);
        assertTrue(exception.getMessage().contains("connector object not present"));
    }

    /** Two manifest formats in one bundle are two sources of truth — a packaging error. */
    @Test
    public void bundlingBothFormatsIsRejected() {
        var exception = expectThrows(ConfigurationException.class,
                () -> ConnectorManifest.load(getClass(), "/manifests/both/connector.manifest"));
        assertTrue(exception.getMessage().contains("connector.manifest.yaml"));
        assertTrue(exception.getMessage().contains("connector.manifest.json"));
    }

    /**
     * A script marked {@code disabled: true} is skipped entirely — treated as if it weren't
     * bundled at all — across every section (schema, authorization, operation), since all three
     * resolve through the same underlying lookup.
     */
    @Test
    public void disabledScriptsAreOmittedFromEverySection() {
        var manifest = ConnectorManifest.load(getClass(), "/manifests/disabled/connector.manifest");

        assertEquals(List.of("/User.native.schema.groovy"), manifest.schemaScripts());
        assertEquals(List.of(), manifest.authorizationScripts());
        assertEquals(List.of("/User.search.all.op.yaml"), manifest.operationScripts());
    }

    /**
     * A disabled script is skipped whether or not it's also the {@code excludedResource} - it was
     * never going to be reloaded as a sibling either way, since it's disabled.
     */
    @Test
    public void disabledScriptStaysOmittedWhenAlsoExcluded() {
        var manifest = ConnectorManifest.load(getClass(), "/manifests/disabled/connector.manifest");

        assertEquals(List.of("/User.native.schema.groovy"),
                manifest.schemaScripts("/User.connid.schema.groovy"));
    }

    /** Excluding the one script that's still enabled leaves only the (already-omitted) disabled one out too. */
    @Test
    public void excludingTheEnabledScriptLeavesOnlyDisabledOnesOut() {
        var manifest = ConnectorManifest.load(getClass(), "/manifests/disabled/connector.manifest");

        assertEquals(List.of(), manifest.schemaScripts("/User.native.schema.groovy"));
    }
}
