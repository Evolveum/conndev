/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.groovy;

import groovy.lang.GroovyShell;
import org.testng.annotations.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

public class GroovyScriptValidatorTest {

    private static groovy.lang.Script parse(String scriptText) {
        return new GroovyShell().parse(scriptText);
    }

    @Test
    public void compileOnlySkipsExecution() {
        var result = GroovyScriptValidator.validate(GroovyScriptValidatorTest::parse,
                "throw new RuntimeException('should not run')", ScriptValidationRequest.SCRIPT_OPERATION_COMPILE);

        assertEquals(result.status(), ScriptValidationResult.Status.OK);
    }

    @Test
    public void compileErrorReportsLineAndColumn() {
        var result = GroovyScriptValidator.validate(
                GroovyScriptValidatorTest::parse, "def x = ", ScriptValidationRequest.SCRIPT_OPERATION_COMPILE);

        assertEquals(result.status(), ScriptValidationResult.Status.ERROR);
        var error = result.errors().get(0);
        assertEquals(error.phase(), ScriptError.Phase.COMPILE);
        assertNotNull(error.line());
        assertNotNull(error.column());
    }

    @Test
    public void buildRunsAndBuildsTheTarget() {
        var built = new AtomicBoolean(false);

        var result = GroovyScriptValidator.validate(
                GroovyScriptValidatorTest::parse, () -> built.set(true), "1 + 1", ScriptValidationRequest.SCRIPT_OPERATION_BUILD);

        assertEquals(result.status(), ScriptValidationResult.Status.OK);
        assertEquals(built.get(), true);
    }

    @Test
    public void noBuildOverloadSkipsBuildForSchemaLikeTargets() {
        var result = GroovyScriptValidator.validate(
                GroovyScriptValidatorTest::parse, "1 + 1", ScriptValidationRequest.SCRIPT_OPERATION_BUILD);

        assertEquals(result.status(), ScriptValidationResult.Status.OK);
    }

    @Test
    public void evaluateErrorIsReportedAsEvaluatePhase() {
        var result = GroovyScriptValidator.validate(
                GroovyScriptValidatorTest::parse, "throw new IllegalStateException('boom')", ScriptValidationRequest.SCRIPT_OPERATION_BUILD);

        assertEquals(result.status(), ScriptValidationResult.Status.ERROR);
        assertEquals(result.errors().get(0).phase(), ScriptError.Phase.EVALUATE);
    }

    @Test
    public void buildErrorIsReportedAsBuildPhase() {
        Runnable failingBuild = () -> {
            throw new IllegalStateException("build failed");
        };

        var result = GroovyScriptValidator.validate(
                GroovyScriptValidatorTest::parse, failingBuild, "1 + 1", ScriptValidationRequest.SCRIPT_OPERATION_BUILD);

        assertEquals(result.status(), ScriptValidationResult.Status.ERROR);
        assertEquals(result.errors().get(0).phase(), ScriptError.Phase.BUILD);
    }
}
