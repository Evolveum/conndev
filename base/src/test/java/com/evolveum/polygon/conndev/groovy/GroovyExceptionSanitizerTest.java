/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.groovy;

import com.evolveum.polygon.conndev.api.ContextLookup;
import com.evolveum.polygon.conndev.concepts.GroovyClosures;
import com.evolveum.polygon.conndev.schema.BaseSchemaBuilder;
import com.evolveum.polygon.conndev.yaml.GroovyScriptCompiler;
import groovy.lang.Closure;
import groovy.lang.GroovyRuntimeException;
import groovy.lang.GroovyShell;
import groovy.lang.MissingMethodException;
import org.codehaus.groovy.runtime.typehandling.GroovyCastException;
import org.identityconnectors.framework.spi.Configuration;
import org.identityconnectors.framework.spi.Connector;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotSame;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

/**
 * Tests that Groovy exceptions thrown while executing connector scripts do not carry runtime
 * values (credentials, arguments) in the messages surfaced to the user. Reproduces bug 12386:
 * a generated script calling a missing method with a username/password leaked
 * <code>values: [apikey, foobaaar]</code> into the connector wizard UI.
 */
public class GroovyExceptionSanitizerTest {

    private static final String SECRET_USERNAME = "apikey";
    private static final String SECRET_TOKEN = "foobaaar";

    // ========================================================================
    // Sanitizer: MissingMethodException (the reported leak)
    // ========================================================================

    private MissingMethodException missingMethodException() {
        try {
            new GroovyShell().evaluate(
                    "def request = new Object(); request.basicAuth('" + SECRET_USERNAME + "', '" + SECRET_TOKEN + "')");
            fail("expected MissingMethodException");
            return null;
        } catch (MissingMethodException e) {
            return e;
        }
    }

    @Test
    public void missingMethodException_argumentValuesAreRedacted() {
        var original = missingMethodException();
        assertTrue(original.getMessage().contains(SECRET_TOKEN));

        MissingMethodException sanitized = GroovyExceptionSanitizer.sanitize(original);

        assertNotSame(original, sanitized);
        assertTrue(sanitized instanceof MissingMethodException);
        assertTrue(sanitized.getMessage().contains("values: [" + GroovyExceptionSanitizer.REDACTED + ", "
                + GroovyExceptionSanitizer.REDACTED + "]"));
        assertFalse(sanitized.getMessage().contains(SECRET_USERNAME));
        assertFalse(sanitized.getMessage().contains(SECRET_TOKEN));
        assertEquals(0, sanitized.getArguments().length);
    }

    @Test
    public void missingMethodException_methodAndTypeInformationIsPreserved() {
        var sanitized = GroovyExceptionSanitizer.sanitize(missingMethodException());

        assertTrue(sanitized.getMessage().contains("basicAuth"));
        assertTrue(sanitized.getMessage().contains("argument types: (String, String)"));
    }

    @Test
    public void missingMethodException_causeChainIsSanitized() {
        var wrapped = new RuntimeException("wrapped script failure", missingMethodException());

        RuntimeException sanitized = GroovyExceptionSanitizer.sanitize(wrapped);

        assertNotSame(wrapped, sanitized);
        assertTrue(sanitized.getCause() instanceof MissingMethodException);
        assertFalse(String.valueOf(sanitized.getCause()).contains(SECRET_TOKEN));
        assertEquals(sanitized.getStackTrace().length, wrapped.getStackTrace().length);
    }

    // ========================================================================
    // Sanitizer: other Groovy built-in exceptions that embed values
    // ========================================================================

    @Test
    public void castException_castValueIsRedacted() {
        var original = new GroovyCastException(
                "Cannot cast object 'top-secret' with class 'java.lang.String' to class 'java.lang.Integer'");
        assertTrue(original.getMessage().contains("top-secret"));

        GroovyCastException sanitized = GroovyExceptionSanitizer.sanitize(original);

        assertNotSame(original, sanitized);
        assertEquals(sanitized.getMessage(),
                "Cannot cast object '" + GroovyExceptionSanitizer.REDACTED + "' with class 'java.lang.String' to class 'java.lang.Integer'");
        assertFalse(sanitized.getMessage().contains("top-secret"));
    }

    @Test
    public void comparisonFailure_bothValuesAreRedacted() {
        var original = new IllegalArgumentException(
                "Cannot compare java.lang.String with value 'secret-value' and java.lang.Integer with value '42'");

        var sanitized = GroovyExceptionSanitizer.sanitize(original);

        assertEquals(sanitized.getMessage(),
                "Cannot compare java.lang.String with value '****' and java.lang.Integer with value '****'");
    }

    @Test
    public void spreadFailure_valueIsRedacted() {
        var original = new IllegalArgumentException("Cannot spread the type java.lang.Integer with value top-secret");

        var sanitized = GroovyExceptionSanitizer.sanitize(original);

        assertEquals(sanitized.getMessage(), "Cannot spread the type java.lang.Integer with value ****");
    }

    @Test
    public void spreadMapFailure_valueIsRedacted() {
        var original = new GroovyRuntimeException("Cannot spread the map java.util.HashMap, value top-secret");

        var sanitized = GroovyExceptionSanitizer.sanitize(original);

        assertEquals(sanitized.getMessage(), "Cannot spread the map java.util.HashMap, value ****");
    }

    @Test
    public void numberOperationFailure_valueIsRedacted() {
        var original = new UnsupportedOperationException(
                "Cannot use rightShiftUnsigned() on this number type: java.math.BigInteger with value: top-secret");
        var shifted = new UnsupportedOperationException(
                "Shift distance must be an integral type, but top-secret (class java.lang.String) was supplied");

        assertEquals(GroovyExceptionSanitizer.sanitize(original).getMessage(),
                "Cannot use rightShiftUnsigned() on this number type: java.math.BigInteger with value: ****");
        assertEquals(GroovyExceptionSanitizer.sanitize(shifted).getMessage(),
                "Shift distance must be an integral type, but **** (class java.lang.String) was supplied");
    }

    @Test
    public void sprintfFailure_argumentIsRedacted() {
        var original = new RuntimeException("sprintf(String,top-secret format)");

        assertEquals(GroovyExceptionSanitizer.sanitize(original).getMessage(), "sprintf(String,****)");
    }

    @Test
    public void rangeFailure_valuesAreRedacted() {
        var original = new GroovyRuntimeException(
                "The argument (secret-to) to downto() cannot be greater than the value (secret-self) it's called on.");

        assertEquals(GroovyExceptionSanitizer.sanitize(original).getMessage(),
                "The argument (****) to downto() cannot be greater than the value (****) it's called on.");
    }

    @Test
    public void castCauseTail_embeddedCauseMessageIsRedacted() {
        var original = new GroovyCastException(
                "Cannot cast object 'top-secret' with class 'java.lang.String' to class 'java.lang.Integer' due to: java.lang.NumberFormatException: For input string: \"top-secret\"");

        var sanitized = GroovyExceptionSanitizer.sanitize(original);

        assertEquals(sanitized.getMessage(),
                "Cannot cast object '****' with class 'java.lang.String' to class 'java.lang.Integer' due to: java.lang.NumberFormatException");
    }

    @Test
    public void nonLeakingException_isReturnedUnchanged() {
        var original = new IllegalStateException("boom");

        assertSame(original, GroovyExceptionSanitizer.sanitize(original));
    }

    private static final class NearMissHolder {
        Object basicAuthentication(String user, String token) {
            return null;
        }
    }

    @Test
    public void missingMethodException_withSuggestionSuffix_valuesAreStillRedacted() {
        MissingMethodException original = null;
        try {
            var shell = new GroovyShell();
            shell.setVariable("holder", new NearMissHolder());
            shell.evaluate("holder.basicAuth('" + SECRET_USERNAME + "', '" + SECRET_TOKEN + "')");
            fail("expected MissingMethodException");
        } catch (MissingMethodException e) {
            original = e;
        }
        assertTrue(original.getMessage().contains(SECRET_TOKEN));

        MissingMethodException sanitized = GroovyExceptionSanitizer.sanitize(original);

        assertFalse(sanitized.getMessage().contains(SECRET_USERNAME));
        assertFalse(sanitized.getMessage().contains(SECRET_TOKEN));
        assertTrue(sanitized.getMessage().contains("values: [" + GroovyExceptionSanitizer.REDACTED + ", "
                + GroovyExceptionSanitizer.REDACTED + "]"));
    }

    @Test
    public void sanitize_isIdempotent() {
        var once = GroovyExceptionSanitizer.sanitize(missingMethodException());
        var twice = GroovyExceptionSanitizer.sanitize(once);

        assertSame(once, twice);
        assertTrue(twice.getMessage().contains("values: [" + GroovyExceptionSanitizer.REDACTED + ", "
                + GroovyExceptionSanitizer.REDACTED + "]"));
    }

    @Test
    public void redactMessage_nullIsNull() {
        assertEquals(GroovyExceptionSanitizer.redactMessage(null), null);
    }

    // ========================================================================
    // Handler wiring: execution contexts propagate sanitized exceptions
    // ========================================================================

    private static final class StubConnector implements Connector {
        @Override public Configuration getConfiguration() { return null; }
        @Override public void init(Configuration c) {}
        @Override public void dispose() {}
    }

    private static final class RequestHolder {
        final Object request = new Object();
    }

    @SuppressWarnings({"GroovyAssignabilityCheck", "unchecked"})
    private static Closure<?> basicAuthClosure() {
        return (Closure<?>) new GroovyShell()
                .evaluate("return { request.basicAuth('" + SECRET_USERNAME + "', '" + SECRET_TOKEN + "') }");
    }

    @Test
    public void closureExecution_failureIsSanitized() {
        try {
            GroovyClosures.copyAndCall(basicAuthClosure(), new RequestHolder());
            fail("expected MissingMethodException");
        } catch (MissingMethodException e) {
            assertFalse(e.getMessage().contains(SECRET_USERNAME));
            assertFalse(e.getMessage().contains(SECRET_TOKEN));
            assertTrue(e.getMessage().contains("values: [" + GroovyExceptionSanitizer.REDACTED + ", "
                    + GroovyExceptionSanitizer.REDACTED + "]"));
        }
    }

    @Test
    public void initializationClosure_failureIsSanitized() {
        try {
            GroovyClosures.callAndReturnDelegate(basicAuthClosure(), new RequestHolder());
            fail("expected MissingMethodException");
        } catch (MissingMethodException e) {
            assertFalse(e.getMessage().contains(SECRET_TOKEN));
        }
    }

    @Test
    public void schemaScript_failureIsSanitized() {
        var loader = new GroovySchemaLoader(new GroovyContext(),
                new BaseSchemaBuilder(StubConnector.class, ContextLookup.none()));

        try {
            loader.load("broken('secret-argument')");
            fail("expected MissingMethodException");
        } catch (MissingMethodException e) {
            assertFalse(e.getMessage().contains("secret-argument"));
            assertTrue(e.getMessage().contains("values: [" + GroovyExceptionSanitizer.REDACTED + "]"));
        }
    }

    @Test
    public void scriptCompilerEvaluate_failureIsSanitized() {
        var compiler = new GroovyScriptCompiler(new GroovyContext());

        try {
            compiler.evaluate("request.basicAuth('" + SECRET_USERNAME + "', '" + SECRET_TOKEN + "')",
                    new RequestHolder());
            fail("expected MissingMethodException");
        } catch (MissingMethodException e) {
            assertFalse(e.getMessage().contains(SECRET_TOKEN));
        }
    }

    @Test
    public void validator_reportsSanitizedMessage() {
        var result = GroovyScriptValidator.validate(
                text -> new GroovyShell().parse(text),
                () -> { },
                "def request = new Object(); request.basicAuth('" + SECRET_USERNAME + "', '" + SECRET_TOKEN + "')",
                ScriptValidationRequest.SCRIPT_OPERATION_BUILD);

        assertEquals(result.status(), ScriptValidationResult.Status.ERROR);
        var error = result.errors().getFirst();
        assertEquals(error.phase(), ScriptError.Phase.EVALUATE);
        assertFalse(error.message().contains(SECRET_USERNAME));
        assertFalse(error.message().contains(SECRET_TOKEN));
        assertTrue(error.message().contains("values: [" + GroovyExceptionSanitizer.REDACTED + ", "
                + GroovyExceptionSanitizer.REDACTED + "]"));
    }
}
