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
import org.identityconnectors.framework.spi.Configuration;
import org.identityconnectors.framework.spi.PoolableConnector;
import org.testng.annotations.Test;

import static org.testng.Assert.*;

/**
 * {@link ClassHandlerConnectorBase} poolability: the base itself implements
 * {@link PoolableConnector}, so every connector built on it is poolable; {@link #checkAlive()}
 * reflects the disposed state; and {@link #shouldReinitialize()} drives the full-reinit-on-each-call
 * behavior from the development-mode setting (and the legacy {@code reinitializeOnEachCall} flag)
 * instead of a purely static choice.
 */
public class ClassHandlerConnectorBasePoolableTest {

    private static class TestConfiguration extends BaseGroovyConnectorConfiguration {
    }

    private static class TestContext implements ConnectorContext {
        private final boolean developmentMode;
        private TestContext(boolean developmentMode) {
            this.developmentMode = developmentMode;
        }
        @Override public ObjectClassHandler handlerFor(ObjectClass objectClass) {
            throw new UnsupportedOperationException("Not needed for this test");
        }
        @Override public BaseSchema schema() {
            throw new UnsupportedOperationException("Not needed for this test");
        }
        @Override public boolean getDevelopmentMode() {
            return developmentMode;
        }
        @Override public <T extends RetrievableContext> T getUnchecked(Class<T> contextType) {
            return null;
        }
    }

    private static class TestConnector extends ClassHandlerConnectorBase<TestContext> {
        private final TestConfiguration configuration = new TestConfiguration();
        private int contextAccessorCalls;

        private TestConnector(boolean reinitializeOnEachCall) {
            super(reinitializeOnEachCall);
        }

        @Override
        public TestContext context() {
            contextAccessorCalls++;
            return context;
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
        public void init(Configuration cfg) {
            context = new TestContext(Boolean.TRUE.equals(configuration.getDevelopmentMode()));
            coreInitialized = false;
            fullyInitialized = false;
            disposed.set(false);
        }

        @Override
        public void dispose() {
            disposed.set(true);
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
        protected ScriptValidationResult validateScript(ScriptValidationRequest request) {
            throw new UnsupportedOperationException("Not needed for this test");
        }
    }

    private static TestConnector connector(boolean reinitializeOnEachCall, boolean developmentMode) {
        var connector = new TestConnector(reinitializeOnEachCall);
        connector.configuration.setDevelopmentMode(developmentMode);
        return connector;
    }

    @Test
    public void baseImplementsPoolableConnector() {
        assertTrue(new TestConnector(false) instanceof PoolableConnector);
    }

    @Test
    public void checkAlivePassesAfterInitAndThrowsAfterDispose() {
        var connector = new TestConnector(false);
        connector.init(null);
        connector.checkAlive();
        connector.dispose();
        try {
            connector.checkAlive();
            fail("Expected IllegalStateException");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("disposed"));
        }
    }

    @Test
    public void shouldReinitializeReflectsDevelopmentModeAndFlag() {
        var production = connector(false, false);
        production.init(null);
        assertFalse(production.shouldReinitialize());

        var dev = connector(false, true);
        dev.init(null);
        assertTrue(dev.shouldReinitialize());

        var flagged = connector(true, false);
        flagged.init(null);
        assertTrue(flagged.shouldReinitialize());
    }

    @Test
    public void shouldReinitializeBeforeInitFallsBackToFlag() {
        assertFalse(new TestConnector(false).shouldReinitialize());
        assertTrue(new TestConnector(true).shouldReinitialize());
    }

    @Test
    public void shouldReinitializeReadsContextFieldNotAccessor() {
        var connector = connector(false, true);
        connector.init(null);
        connector.shouldReinitialize();
        assertEquals(connector.contextAccessorCalls, 0,
                "shouldReinitialize must not trigger the context() accessor (subclasses may init from it)");
    }
}
