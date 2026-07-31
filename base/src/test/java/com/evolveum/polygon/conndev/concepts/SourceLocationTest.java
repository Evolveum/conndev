/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.concepts;

import com.evolveum.polygon.conndev.api.ContextLookup;
import com.evolveum.polygon.conndev.groovy.GroovyContext;
import com.evolveum.polygon.conndev.schema.BaseAttributeDefinition;
import com.evolveum.polygon.conndev.schema.BaseObjectClassDefinition;
import com.evolveum.polygon.conndev.schema.BaseSchema;
import com.evolveum.polygon.conndev.schema.BaseSchemaBuilder;
import groovy.lang.GroovyShell;
import org.codehaus.groovy.runtime.MethodClosure;
import org.identityconnectors.framework.spi.Configuration;
import org.identityconnectors.framework.spi.Connector;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.InputStreamReader;
import java.io.StringReader;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests that verify {@link SourceLocation} is captured correctly from Groovy connector
 * definition scripts when {@link DevelopmentMode} is enabled.
 *
 * <p>{@link SourceLocation#capture()} inspects the current thread's stack trace looking for
 * a frame whose file name ends with {@code groovy}. That frame is only present when the Groovy
 * script has been evaluated with an explicit file name (see the {@code User.groovy} loading in
 * {@link #loadSchema(String, String)}). When development mode is disabled, every capture must
 * return {@link SourceLocation#UNKNOWN}.
 *
 * <p>The sample definition script used here is {@code /openProject/User.groovy}.
 */
public class SourceLocationTest {

    private static final String USER_GROOVY = "/openProject/User.groovy";
    private static final String USER_GROOVY_FILE = "User.groovy";

    // ========================================================================
    // Test fixtures
    // ========================================================================

    /** Stub connector required by {@link BaseSchemaBuilder}; no operations are performed. */
    private static final class StubConnector implements Connector {
        @Override public Configuration getConfiguration() { return null; }
        @Override public void init(Configuration c) {}
        @Override public void dispose() {}
    }

    /** No-op ContextLookup: only schema parsing is exercised, no runtime scripts run. */
    private static final ContextLookup NOOP_CONTEXT = new ContextLookup() {
        @Override
        public <T extends RetrievableContext> T get(Class<T> contextType) throws IllegalStateException {
            throw new IllegalStateException("No context registered for " + contextType);
        }
    };

    @BeforeMethod
    public void resetDevelopmentMode() {
        // Ensure a clean, known starting state for every test.
        DevelopmentMode.unset();
    }

    @AfterMethod
    public void clearDevelopmentMode() {
        DevelopmentMode.unset();
    }

    // ========================================================================
    // Helpers
    // ========================================================================

    /**
     * Loads a Groovy schema resource, evaluating it with an explicit file name so Groovy records
     * that file name in stack traces (which is what {@link SourceLocation#capture()} inspects).
     */
    private BaseSchema<BaseObjectClassDefinition<BaseAttributeDefinition>> loadSchema(String resourcePath, String fileName) {
        var builder = new BaseSchemaBuilder(StubConnector.class, NOOP_CONTEXT);
        GroovyShell shell = new GroovyContext().createShell();
        shell.setVariable("objectClass", new MethodClosure(builder, "objectClass"));
        shell.setVariable("relationship", new MethodClosure(builder, "relationship"));
        try (var is = SourceLocationTest.class.getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new IllegalArgumentException("Resource not found: " + resourcePath);
            }
            shell.evaluate(new InputStreamReader(is, StandardCharsets.UTF_8), fileName);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load " + resourcePath, e);
        }
        return builder.build();
    }

    /**
     * Reads the {@link SourceLocation} recorded on an attribute's {@code remoteName}
     * {@link DefinitionValue} (the definition value captured when the {@code attribute("...")}
     * DSL call is parsed).
     */
    private SourceLocation remoteNameLocation(BaseAttributeDefinition attribute) {
        try {
            Field field = BaseAttributeDefinition.class.getDeclaredField("remoteName");
            field.setAccessible(true);
            DefinitionValue<?> value = (DefinitionValue<?>) field.get(attribute);
            return value.location();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot read remoteName location", e);
        }
    }

    // ========================================================================
    // 1. DevelopmentMode basic behavior
    // ========================================================================

    @Test
    public void developmentMode_defaultsToDisabled() {
        assertThat(DevelopmentMode.isEnabled()).isFalse();
    }

    @Test
    public void developmentMode_setEnablesAndUnsetDisables() {
        DevelopmentMode.set(true);
        assertThat(DevelopmentMode.isEnabled()).isTrue();

        DevelopmentMode.set(false);
        assertThat(DevelopmentMode.isEnabled()).isFalse();

        DevelopmentMode.set(true);
        DevelopmentMode.unset();
        assertThat(DevelopmentMode.isEnabled()).isFalse();
    }

    @Test
    public void developmentMode_isThreadLocal() throws InterruptedException {
        DevelopmentMode.set(true);

        var otherThreadValue = new boolean[]{true};
        Thread other = new Thread(() -> otherThreadValue[0] = DevelopmentMode.isEnabled());
        other.start();
        other.join();

        // The other thread must not see this thread's development mode flag.
        assertThat(otherThreadValue[0]).isFalse();
        assertThat(DevelopmentMode.isEnabled()).isTrue();
    }

    // ========================================================================
    // 2. capture() when development mode is DISABLED -> always UNKNOWN
    // ========================================================================

    @Test
    public void capture_developmentModeDisabled_returnsUnknown() {
        assertThat(DevelopmentMode.isEnabled()).isFalse();
        assertThat(SourceLocation.capture()).isSameAs(SourceLocation.UNKNOWN);
    }

    @Test
    public void captureWithExtensions_developmentModeDisabled_returnsUnknown() {
        assertThat(SourceLocation.capture("groovy", "java")).isSameAs(SourceLocation.UNKNOWN);
    }

    @Test
    public void schema_developmentModeDisabled_attributeLocationsAreUnknown() {
        // Even though the script is loaded with a file name, capture() short-circuits to UNKNOWN
        // because development mode is disabled.
        assertThat(DevelopmentMode.isEnabled()).isFalse();
        BaseSchema schema = loadSchema(USER_GROOVY, USER_GROOVY_FILE);

        BaseAttributeDefinition login = schema.objectClass("User").attributeFromProtocolName("login");
        assertThat(remoteNameLocation(login)).isSameAs(SourceLocation.UNKNOWN);
    }

    // ========================================================================
    // 3. capture() when development mode is ENABLED
    // ========================================================================

    @Test
    public void capture_developmentModeEnabled_fromGroovyScript() {
        DevelopmentMode.set(true);

        // Invoke SourceLocation.capture() from inside a Groovy script that is evaluated with a
        // file name. The captured location must reflect that Groovy file and line.
        GroovyShell shell = new GroovyContext().createShell();
        shell.setVariable("capture", new MethodClosure(SourceLocation.class, "capture"));
        // Line 1 is blank, capture() is invoked on line 2.
        Object result = shell.evaluate(new StringReader("\ndef loc = capture()\nreturn loc\n"),
                "Sample.groovy");

        assertThat(result).isInstanceOf(SourceLocation.class);
        SourceLocation location = (SourceLocation) result;
        assertThat(location).isNotSameAs(SourceLocation.UNKNOWN);
        assertThat(location.name()).isEqualTo("Sample.groovy");
        assertThat(location.line()).isEqualTo(2);
    }

    @Test
    public void capture_developmentModeEnabled_noGroovyOnStack_returnsUnknown() {
        DevelopmentMode.set(true);

        // Called directly from Java (this .java test frame is not a *.groovy frame),
        // so no matching source file is found.
        assertThat(SourceLocation.capture()).isSameAs(SourceLocation.UNKNOWN);
    }

    // ========================================================================
    // 4. openProject/User.groovy schema captures locations for every attribute
    // ========================================================================

    @Test
    public void userGroovy_allAttributesHaveGroovySourceLocation() {
        DevelopmentMode.set(true);
        var schema = loadSchema(USER_GROOVY, USER_GROOVY_FILE);
        var user = schema.objectClass("User");

        assertThat(user).isNotNull();
        assertThat(user.attributes()).isNotEmpty();

        for (BaseAttributeDefinition attribute : user.attributes()) {
            SourceLocation location = remoteNameLocation(attribute);
            assertThat(location)
                    .as("attribute %s should have a captured source location", attribute.remoteName())
                    .isNotSameAs(SourceLocation.UNKNOWN);
            assertThat(location.name())
                    .as("attribute %s captured file name", attribute.remoteName())
                    .isEqualTo(USER_GROOVY_FILE);
            assertThat(location.line())
                    .as("attribute %s captured line number", attribute.remoteName())
                    .isGreaterThan(0);
        }
    }

    @Test
    public void userGroovy_idAttributeCapturedAtDeclaredLine() {
        DevelopmentMode.set(true);
        var schema = loadSchema(USER_GROOVY, USER_GROOVY_FILE);

        // In /openProject/User.groovy the `attribute("id")` block starts at line 10.
        BaseAttributeDefinition id = schema.objectClass("User").attributeFromProtocolName("id");
        SourceLocation location = remoteNameLocation(id);

        assertThat(location.name()).isEqualTo(USER_GROOVY_FILE);
        assertThat(location.line()).isEqualTo(10);
        assertThat(location).hasToString("User.groovy:10");
    }

    @Test
    public void userGroovy_loginAttributeCapturedAtDeclaredLine() {
        DevelopmentMode.set(true);
        var schema = loadSchema(USER_GROOVY, USER_GROOVY_FILE);

        // `attribute("login")` starts at line 17.
        BaseAttributeDefinition login = schema.objectClass("User").attributeFromProtocolName("login");
        assertThat(remoteNameLocation(login).line()).isEqualTo(17);
    }

    @Test
    public void userGroovy_attributeLocationsAreInSourceOrder() {
        DevelopmentMode.set(true);
        var schema = loadSchema(USER_GROOVY, USER_GROOVY_FILE);

        // Attributes declared later in the file must have a strictly greater line number.
        int idLine = remoteNameLocation(schema.objectClass("User").attributeFromProtocolName("id")).line();
        int loginLine = remoteNameLocation(schema.objectClass("User").attributeFromProtocolName("login")).line();
        int passwordLine = remoteNameLocation(schema.objectClass("User").attributeFromProtocolName("password")).line();

        assertThat(idLine).isLessThan(loginLine);
        assertThat(loginLine).isLessThan(passwordLine);
    }

    @Test
    public void userGroovy_reloadIsDeterministic() {
        DevelopmentMode.set(true);

        int firstLoad = remoteNameLocation(
                loadSchema(USER_GROOVY, USER_GROOVY_FILE).objectClass("User").attributeFromProtocolName("email")).line();
        int secondLoad = remoteNameLocation(
                loadSchema(USER_GROOVY, USER_GROOVY_FILE).objectClass("User").attributeFromProtocolName("email")).line();

        assertThat(firstLoad).isEqualTo(secondLoad);
    }
}
