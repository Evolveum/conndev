/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.spi;

import com.evolveum.polygon.conndev.api.ContextLookup;
import com.evolveum.polygon.conndev.groovy.BaseGroovyConnectorConfiguration;
import com.evolveum.polygon.conndev.groovy.ScriptValidationRequest;
import com.evolveum.polygon.conndev.groovy.ScriptValidationResult;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.Schema;
import org.identityconnectors.framework.common.objects.ScriptContext;
import org.identityconnectors.framework.spi.Configuration;
import org.testng.annotations.Test;

import java.util.Map;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

/**
 * {@link ClassHandlerConnectorBase#runScriptOnResource} dispatch: development-mode gate,
 * {@code operation} validation, and routing to {@link ClassHandlerConnectorBase#validateScript}
 * — independent of any concrete connector family.
 */
public class ClassHandlerConnectorBaseScriptValidationTest {

    private static class TestConfiguration extends BaseGroovyConnectorConfiguration {
    }

    private static class TestConnector extends ClassHandlerConnectorBase {
        private final TestConfiguration configuration = new TestConfiguration();
        private ScriptValidationResult validateScriptResult = ScriptValidationResult.ok();
        private RuntimeException validateScriptFailure;
        private ScriptValidationRequest capturedRequest;

        @Override
        public ContextLookup context() {
            return ContextLookup.none();
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

    private static ScriptContext scriptContext(String scriptText, Map<String, Object> arguments) {
        return new ScriptContext("groovy", scriptText, arguments);
    }

    @Test
    public void rejectsWhenNotInDevelopmentMode() {
        var connector = new TestConnector();
        connector.configuration.setDevelopmentMode(false);
        var context = scriptContext("1 + 1", Map.of(
                ScriptValidationRequest.SCRIPT_ARGUMENT_OPERATION, ScriptValidationRequest.SCRIPT_OPERATION_COMPILE,
                ScriptValidationRequest.SCRIPT_ARGUMENT_ARTIFACT_KIND, "operation"));

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
        var context = new ScriptContext("javascript", "1 + 1", Map.of(
                ScriptValidationRequest.SCRIPT_ARGUMENT_OPERATION, ScriptValidationRequest.SCRIPT_OPERATION_COMPILE,
                ScriptValidationRequest.SCRIPT_ARGUMENT_ARTIFACT_KIND, "operation"));

        try {
            connector.runScriptOnResource(context, null);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("javascript"));
        }
    }

    @Test
    public void rejectsUnsupportedOperationValue() {
        var connector = new TestConnector();
        connector.configuration.setDevelopmentMode(true);
        var context = scriptContext("1 + 1", Map.of(
                ScriptValidationRequest.SCRIPT_ARGUMENT_OPERATION, "validate",
                ScriptValidationRequest.SCRIPT_ARGUMENT_ARTIFACT_KIND, "operation"));

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
        var context = scriptContext("objectClass('User') { }", Map.of(
                ScriptValidationRequest.SCRIPT_ARGUMENT_OPERATION, ScriptValidationRequest.SCRIPT_OPERATION_BUILD,
                ScriptValidationRequest.SCRIPT_ARGUMENT_ARTIFACT_KIND, ScriptValidationRequest.ARTIFACT_KIND_SCHEMA,
                ScriptValidationRequest.SCRIPT_ARGUMENT_FILENAME, "/User.schema.groovy"));

        var result = connector.runScriptOnResource(context, null);

        assertEquals(result, Map.of("status", "ok"));
        assertEquals(connector.capturedRequest.artifactKind(), ScriptValidationRequest.ARTIFACT_KIND_SCHEMA);
        assertEquals(connector.capturedRequest.filename(), "/User.schema.groovy");
        assertEquals(connector.capturedRequest.scriptText(), "objectClass('User') { }");
        assertEquals(connector.capturedRequest.operation(), ScriptValidationRequest.SCRIPT_OPERATION_BUILD);
    }

    @Test
    public void missingFilenameIsPassedAsNull() {
        var connector = new TestConnector();
        connector.configuration.setDevelopmentMode(true);
        var context = scriptContext("1 + 1", Map.of(
                ScriptValidationRequest.SCRIPT_ARGUMENT_OPERATION, ScriptValidationRequest.SCRIPT_OPERATION_COMPILE,
                ScriptValidationRequest.SCRIPT_ARGUMENT_ARTIFACT_KIND, "operation"));

        connector.runScriptOnResource(context, null);

        assertEquals(connector.capturedRequest.filename(), null);
    }

    @Test
    public void validateScriptExceptionIsReportedAsInitializationError() {
        var connector = new TestConnector();
        connector.configuration.setDevelopmentMode(true);
        connector.validateScriptFailure = new IllegalStateException("schema not ready");
        var context = scriptContext("1 + 1", Map.of(
                ScriptValidationRequest.SCRIPT_ARGUMENT_OPERATION, ScriptValidationRequest.SCRIPT_OPERATION_COMPILE,
                ScriptValidationRequest.SCRIPT_ARGUMENT_ARTIFACT_KIND, "operation"));

        @SuppressWarnings("unchecked")
        var result = (Map<String, Object>) connector.runScriptOnResource(context, null);

        assertEquals(result.get("status"), "error");
        assertEquals(result.get("phase"), "initialization");
        assertEquals(result.get("message"), "schema not ready");
    }
}
