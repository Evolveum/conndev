/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.spi;

import com.evolveum.polygon.conndev.concepts.RetrievableContext;
import com.evolveum.polygon.conndev.groovy.*;
import com.evolveum.polygon.conndev.schema.BaseSchema;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.Schema;
import org.identityconnectors.framework.common.objects.ScriptContext;
import org.identityconnectors.framework.spi.Configuration;
import org.testng.annotations.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

import static org.testng.Assert.*;

/**
 * {@link ClassHandlerConnectorBase#runScriptOnResource} dispatch: development-mode gate,
 * {@code language}/{@code operation} validation, and routing to {@link
 * ClassHandlerConnectorBase#validateScript} — independent of any concrete connector family.
 * Arguments/results are compared as JSON trees, so expected values can stay naturally formatted
 * instead of matching the compact wire encoding exactly.
 */
public class ClassHandlerConnectorBaseScriptValidationTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static class TestConfiguration extends BaseGroovyConnectorConfiguration {
    }

    /** Minimal stub - none of this test's TestConnector methods actually delegate to it. */
    private static final ConnectorContext STUB_CONTEXT = new ConnectorContext() {
        @Override public ObjectClassHandler handlerFor(ObjectClass objectClass) {
            throw new UnsupportedOperationException("Not needed for this test");
        }
        @Override public BaseSchema schema() {
            throw new UnsupportedOperationException("Not needed for this test");
        }
        @Override public boolean getDevelopmentMode() {
            return false;
        }
        @Override public <T extends RetrievableContext> T getUnchecked(Class<T> contextType) {
            return null;
        }
    };

    private static class TestConnector extends ClassHandlerConnectorBase<ConnectorContext> {
        private final TestConfiguration configuration = new TestConfiguration();
        private ScriptValidationResult validateScriptResult = ScriptValidationResult.ok();
        private RuntimeException validateScriptFailure;
        private ScriptValidationRequest capturedRequest;

        private TestConnector() {
            super(true);
        }

        @Override
        public ConnectorContext context() {
            return STUB_CONTEXT;
        }

        @Override
        public ObjectClassHandler handlerFor(ObjectClass objectClass) {
            throw new UnsupportedOperationException("Not needed for this test");
        }

        @Override
        public Configuration getConfiguration() {
            return configuration;
        }

        @Override
        public void init(Configuration configuration) {
        }

        @Override
        public void dispose() {
        }

        @Override
        protected void initializeSchema(GroovySchemaLoader loader) {
        }

        @Override
        protected void initializeObjectClassHandler(GroovyScriptLoader builder) {
        }

        @Override
        public Schema schema() {
            throw new UnsupportedOperationException("Not needed for this test");
        }

        @Override
        public void test() {
            throw new UnsupportedOperationException("Not needed for this test");
        }

        @Override
        protected ScriptValidationResult validateScript(ScriptValidationRequest request) throws Exception {
            capturedRequest = request;
            if (validateScriptFailure != null) {
                throw validateScriptFailure;
            }
            return validateScriptResult;
        }
    }

    private static ScriptContext scriptContext(String scriptText, String argumentsJson) {
        return new ScriptContext("groovy", scriptText, arguments(argumentsJson));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> arguments(String json) {
        return MAPPER.readValue(json, Map.class);
    }

    private static JsonNode json(String json) {
        return MAPPER.readTree(json);
    }

    private static JsonNode tree(Object value) {
        return MAPPER.valueToTree(value);
    }

    @Test
    public void rejectsWhenNotInDevelopmentMode() {
        var connector = new TestConnector();
        connector.configuration.setDevelopmentMode(false);
        var context = scriptContext("1 + 1", """
                { "operation": "compile", "artifactKind": "operation" }
                """);

        try {
            connector.runScriptOnResource(context, null);
            fail("Expected UnsupportedOperationException");
        } catch (UnsupportedOperationException e) {
            assertTrue(e.getMessage().contains("development mode"));
        }
    }

    @Test
    public void rejectsNonGroovyScriptLanguage() {
        var connector = new TestConnector();
        connector.configuration.setDevelopmentMode(true);
        var context = new ScriptContext("javascript", "1 + 1", arguments("""
                { "operation": "compile", "artifactKind": "operation" }
                """));

        try {
            connector.runScriptOnResource(context, null);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("javascript"));
        }
    }

    @Test
    public void acceptsYamlScriptLanguage() {
        var connector = new TestConnector();
        connector.configuration.setDevelopmentMode(true);
        var context = new ScriptContext("yaml", "objectClasses: {}", arguments("""
                { "operation": "compile", "artifactKind": "schema" }
                """));

        var result = connector.runScriptOnResource(context, null);

        assertEquals(tree(result), json("""
                { "status": "ok" }
                """));
        assertEquals(connector.capturedRequest.language(), "yaml");
        assertTrue(connector.capturedRequest.isYaml());
    }

    @Test
    public void rejectsUnsupportedOperationValue() {
        var connector = new TestConnector();
        connector.configuration.setDevelopmentMode(true);
        var context = scriptContext("1 + 1", """
                { "operation": "validate", "artifactKind": "operation" }
                """);

        try {
            connector.runScriptOnResource(context, null);
            fail("Expected UnsupportedOperationException");
        } catch (UnsupportedOperationException e) {
            assertTrue(e.getMessage().contains(ScriptValidationRequest.SCRIPT_OPERATION_BUILD));
            assertTrue(e.getMessage().contains(ScriptValidationRequest.SCRIPT_OPERATION_COMPILE));
        }
    }

    @Test
    public void routesArtifactKindFilenameAndScriptToValidateScript() {
        var connector = new TestConnector();
        connector.configuration.setDevelopmentMode(true);
        var context = scriptContext("objectClass('User') { }", """
                {
                  "operation": "build",
                  "artifactKind": "schema",
                  "filename": "/User.schema.groovy"
                }
                """);

        var result = connector.runScriptOnResource(context, null);

        assertEquals(tree(result), json("""
                { "status": "ok" }
                """));
        assertEquals(connector.capturedRequest.artifactKind(), ScriptValidationRequest.ARTIFACT_KIND_SCHEMA);
        assertEquals(connector.capturedRequest.filename(), "/User.schema.groovy");
        assertEquals(connector.capturedRequest.scriptText(), "objectClass('User') { }");
        assertEquals(connector.capturedRequest.operation(), ScriptValidationRequest.SCRIPT_OPERATION_BUILD);
    }

    /**
     * "Repair object class" can regenerate several scripts in one response - every one of them
     * needs to stand in for its own old content during validation, not just the one carried as
     * {@code filename}/{@code scriptText}. The extra ones travel as a {@code overrides} script
     * argument (filename -> candidate content).
     */
    @Test
    public void overridesArePassedThroughFromScriptArguments() {
        var connector = new TestConnector();
        connector.configuration.setDevelopmentMode(true);
        var context = scriptContext("objectClass('User') { }", """
                {
                  "operation": "build",
                  "artifactKind": "schema",
                  "filename": "/User.schema.groovy",
                  "overrides": { "/User.search.all.op.yaml": "search { }" }
                }
                """);

        connector.runScriptOnResource(context, null);

        assertEquals(tree(connector.capturedRequest.overrides()), json("""
                { "/User.search.all.op.yaml": "search { }" }
                """));
    }

    /** No {@code overrides} argument at all - e.g. every single-file request today - defaults to an empty map, not null. */
    @Test
    public void missingOverridesArgumentDefaultsToEmptyMap() {
        var connector = new TestConnector();
        connector.configuration.setDevelopmentMode(true);
        var context = scriptContext("1 + 1", """
                { "operation": "compile", "artifactKind": "operation" }
                """);

        connector.runScriptOnResource(context, null);

        assertEquals(tree(connector.capturedRequest.overrides()), json("{}"));
    }

    /**
     * {@link ScriptValidationRequest#allOverrides()} merges the primary {@code filename}/{@code
     * scriptText} pair together with the extra {@code overrides} into one map - the connector-side
     * caller only has to deal with one unified set of substitutions.
     */
    @Test
    public void allOverridesMergesPrimaryFilenameWithExtraOverrides() {
        var connector = new TestConnector();
        connector.configuration.setDevelopmentMode(true);
        var context = scriptContext("objectClass('User') { }", """
                {
                  "operation": "build",
                  "artifactKind": "schema",
                  "filename": "/User.schema.groovy",
                  "overrides": { "/User.search.all.op.yaml": "search { }" }
                }
                """);

        connector.runScriptOnResource(context, null);

        assertEquals(tree(connector.capturedRequest.allOverrides()), json("""
                {
                  "/User.search.all.op.yaml": "search { }",
                  "/User.schema.groovy": "objectClass('User') { }"
                }
                """));
    }

    /** With no primary {@code filename} (e.g. a compile-only check), {@link ScriptValidationRequest#allOverrides()} is just the extra overrides. */
    @Test
    public void allOverridesWithNoPrimaryFilenameIsJustTheExtraOverrides() {
        var connector = new TestConnector();
        connector.configuration.setDevelopmentMode(true);
        var context = scriptContext("1 + 1", """
                { "operation": "compile", "artifactKind": "operation" }
                """);

        connector.runScriptOnResource(context, null);

        assertEquals(tree(connector.capturedRequest.allOverrides()), json("{}"));
    }

    @Test
    public void missingFilenameIsPassedAsNull() {
        var connector = new TestConnector();
        connector.configuration.setDevelopmentMode(true);
        var context = scriptContext("1 + 1", """
                { "operation": "compile", "artifactKind": "operation" }
                """);

        connector.runScriptOnResource(context, null);

        assertEquals(connector.capturedRequest.filename(), null);
    }

    @Test
    public void validateScriptExceptionIsReportedAsInitializationError() {
        var connector = new TestConnector();
        connector.configuration.setDevelopmentMode(true);
        connector.validateScriptFailure = new IllegalStateException("schema not ready");
        var context = scriptContext("1 + 1", """
                { "operation": "compile", "artifactKind": "operation" }
                """);

        var result = connector.runScriptOnResource(context, null);

        assertEquals(tree(result), json("""
                {
                  "status": "error",
                  "errors": [
                    { "status": "error", "phase": "initialization", "message": "schema not ready" }
                  ]
                }
                """));
    }
}
