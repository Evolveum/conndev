/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.spi;

import com.evolveum.polygon.conndev.concepts.DevelopmentMode;
import com.evolveum.polygon.conndev.groovy.GroovyContext;
import groovy.lang.GroovyShell;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.StringReader;

import static org.assertj.core.api.Assertions.assertThat;

public class ConnectorExceptionsTest {

    @BeforeMethod
    public void resetDevelopmentMode() {
        DevelopmentMode.unset();
    }

    @AfterMethod
    public void clearDevelopmentMode() {
        DevelopmentMode.unset();
    }

    /** Evaluates a script that throws on line 2 (line 1 is blank) and returns the caught Throwable. */
    private Throwable evaluateFailingScript(String fileName) {
        GroovyShell shell = new GroovyContext().createShell();
        try {
            shell.evaluate(new StringReader("\nnull.length()\n"), fileName);
        } catch (Throwable t) {
            return t;
        }
        throw new IllegalStateException("Expected script to throw");
    }

    @Test
    public void raiseException_developmentModeEnabled_appendsCauseMessageAndLocation() {
        DevelopmentMode.set(true);
        Throwable cause = evaluateFailingScript("Failing.groovy");

        ConnectorException e = ConnectorExceptions.raiseException("REST pagination failed", cause);

        assertThat(e.getMessage())
                .isEqualTo("REST pagination failed: Cannot invoke method length() on null object [location=Failing.groovy:2]");
        assertThat(e.getCause()).isSameAs(cause);
    }

    @Test
    public void raiseException_appendsCauseMessage_regardlessOfDevelopmentMode() {
        assertThat(DevelopmentMode.isEnabled()).isFalse();
        Throwable cause = new RuntimeException("boom");

        ConnectorException e = ConnectorExceptions.raiseException("REST pagination failed", cause);

        assertThat(e.getMessage()).isEqualTo("REST pagination failed: boom");
        assertThat(e.getCause()).isSameAs(cause);
    }

    @Test
    public void raiseException_developmentModeEnabled_noGroovyFrame_omitsLocation() {
        DevelopmentMode.set(true);
        Throwable cause = new RuntimeException("boom, thrown directly from Java");

        ConnectorException e = ConnectorExceptions.raiseException("REST pagination failed", cause);

        assertThat(e.getMessage()).isEqualTo("REST pagination failed: boom, thrown directly from Java");
    }

    @Test
    public void raiseException_causeWithoutMessage_omitsColon() {
        DevelopmentMode.set(true);
        Throwable cause = new RuntimeException();

        ConnectorException e = ConnectorExceptions.raiseException("REST pagination failed", cause);

        assertThat(e.getMessage()).isEqualTo("REST pagination failed");
    }
}
