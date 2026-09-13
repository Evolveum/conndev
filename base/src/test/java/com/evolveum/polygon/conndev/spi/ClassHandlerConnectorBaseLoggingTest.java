/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.spi;

import com.evolveum.polygon.conndev.api.ContextLookup;
import com.evolveum.polygon.conndev.concepts.RetrievableContext;
import com.evolveum.polygon.conndev.devtools.log.OperationLogParser;
import com.evolveum.polygon.conndev.devtools.log.OperationTrace;
import com.evolveum.polygon.conndev.groovy.BaseGroovyConnectorConfiguration;
import com.evolveum.polygon.conndev.groovy.ConnectorContext;
import com.evolveum.polygon.conndev.groovy.ScriptValidationRequest;
import com.evolveum.polygon.conndev.groovy.ScriptValidationResult;
import com.evolveum.polygon.conndev.logging.CapturingLogProvider;
import com.evolveum.polygon.conndev.schema.BaseSchema;
import org.identityconnectors.framework.common.objects.*;
import org.identityconnectors.framework.spi.Configuration;
import org.testng.annotations.Test;

import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/**
 * Verifies that {@link ClassHandlerConnectorBase} bridges the {@link ConnectorContext}
 * development-mode configuration to the structured logging of dispatched operations.
 */
public class ClassHandlerConnectorBaseLoggingTest {

    private static final ObjectClass ACCOUNT = new ObjectClass("account");
    private static final OperationOptions OPTIONS = new OperationOptions(Map.of());

    @Test
    public void developmentModeEnabledEmitsStructuredOperationEntries() {
        var connector = new TestConnector(true);
        CapturingLogProvider.clear();
        var uid = connector.create(ACCOUNT, Set.of(AttributeBuilder.build("name", "Alice")), OPTIONS);
        var updated = connector.updateDelta(ACCOUNT, uid,
                Set.of(new AttributeDeltaBuilder().setName("title").addValueToReplace("Engineer").build()), OPTIONS);

        assertEquals(updated.size(), 1);
        var lines = CapturingLogProvider.lines();
        assertTrue(lines.stream().allMatch(OperationLogParser::isStructuredLine));
        var traces = OperationLogParser.parse(lines);
        assertEquals(traces.size(), 2);
        assertEquals(traces.getFirst().operation(), "create");
        assertEquals(traces.get(1).operation(), "update");
        assertTrue(traces.stream().allMatch(OperationTrace::completed));
        assertTrue(traces.stream().allMatch(trace -> trace.outcome().ok()));
    }

    @Test
    public void developmentModeDisabledEmitsPlainLinesWithoutMarker() {
        var connector = new TestConnector(false);
        CapturingLogProvider.clear();
        connector.create(ACCOUNT, Set.of(AttributeBuilder.build("name", "Alice")), OPTIONS);

        var lines = CapturingLogProvider.lines();
        assertTrue(lines.stream().noneMatch(OperationLogParser::isStructuredLine));
        assertTrue(lines.stream().anyMatch(line -> line.startsWith("create on account: Create account")));
        assertTrue(lines.stream().anyMatch(line -> line.contains("completed")));
    }

    @Test
    public void developmentModeToggledAtRuntimeIsHonoredPerOperation() {
        var connector = new TestConnector(false);
        var configuration = (TestConfiguration) connector.getConfiguration();

        // instantiated with development mode off: plain lines
        CapturingLogProvider.clear();
        var uid = connector.create(ACCOUNT, Set.of(AttributeBuilder.build("name", "Alice")), OPTIONS);
        assertTrue(CapturingLogProvider.lines().stream().noneMatch(OperationLogParser::isStructuredLine));

        // enable development mode on the live configuration, no re-instantiation
        configuration.setDevelopmentMode(true);
        CapturingLogProvider.clear();
        connector.updateDelta(ACCOUNT, uid,
                Set.of(new AttributeDeltaBuilder().setName("title").addValueToReplace("Engineer").build()),
                OPTIONS);
        var lines = CapturingLogProvider.lines();
        assertTrue(lines.stream().allMatch(OperationLogParser::isStructuredLine));
        var traces = OperationLogParser.parse(lines);
        assertEquals(traces.size(), 1);
        assertEquals(traces.getFirst().operation(), "update");
        assertTrue(traces.getFirst().completed());
        assertTrue(traces.getFirst().outcome().ok());

        // disable again: plain lines resume
        configuration.setDevelopmentMode(false);
        CapturingLogProvider.clear();
        connector.create(ACCOUNT, Set.of(AttributeBuilder.build("name", "Bob")), OPTIONS);
        assertTrue(CapturingLogProvider.lines().stream().noneMatch(OperationLogParser::isStructuredLine));
    }

    private static final class TestConfiguration extends BaseGroovyConnectorConfiguration {
    }

    private static final class TestConnector extends ClassHandlerConnectorBase {

        private final TestContext context;
        private final TestConfiguration configuration = new TestConfiguration();

        private TestConnector(boolean developmentMode) {
            configuration.setDevelopmentMode(developmentMode);
            // read live, like RestConnectorContext does, so runtime toggles are honored
            this.context = new TestContext(configuration::getDevelopmentMode);
        }

        @Override
        public ContextLookup context() {
            return context;
        }

        @Override
        public ObjectClassHandler handlerFor(ObjectClass objectClass) {
            return context.handlerFor(objectClass);
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
        protected ScriptValidationResult validateScript(ScriptValidationRequest request) {
            throw new UnsupportedOperationException("Not supported in this test");
        }

        @Override
        public Schema schema() {
            return null;
        }

        @Override
        public void test() {
            throw new UnsupportedOperationException("Not supported in this test");
        }
    }

    private static final class TestContext implements ConnectorContext {

        private final Supplier<Boolean> developmentMode;

        private TestContext(Supplier<Boolean> developmentMode) {
            this.developmentMode = developmentMode;
        }

        @Override
        public ObjectClassHandler handlerFor(ObjectClass objectClass) {
            ObjectCreateOperation create = (attributes, options) -> new ConnectorObjectBuilder()
                    .setObjectClass(objectClass).setUid(new Uid("1")).setName("alice").build();
            ObjectUpdateOperation update = (uid, modifications, options) -> modifications;
            return new CompositeObjectClassHandler(objectClass, Map.of(
                    ObjectCreateOperation.class, create,
                    ObjectUpdateOperation.class, update));
        }

        @Override
        public BaseSchema schema() {
            return null;
        }

        @Override
        public boolean getDevelopmentMode() {
            return Boolean.TRUE.equals(developmentMode.get());
        }

        @Override
        public <T extends RetrievableContext> T getUnchecked(Class<T> type) {
            return type.isInstance(this) ? type.cast(this) : null;
        }
    }
}
