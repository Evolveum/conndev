/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.groovy;

import org.codehaus.groovy.control.CompilationFailedException;
import org.codehaus.groovy.control.MultipleCompilationErrorsException;
import org.codehaus.groovy.control.messages.SyntaxErrorMessage;
import org.codehaus.groovy.syntax.SyntaxException;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.Function;

/**
 * Validates a candidate Groovy script against a throwaway target, without touching the deployed
 * scripts or the target system. Used by connector development-mode script editors ({@code
 * ScriptOnResourceOp}) to report compile/evaluate/build errors before a script is saved.
 */
public final class GroovyScriptValidator {

    private GroovyScriptValidator() {
    }

    /**
     * Compiles {@code scriptText} via {@code parse}, and — unless {@code operation} is {@link
     * ScriptValidationRequest#SCRIPT_OPERATION_COMPILE} — also evaluates it. Used for scripts with
     * no build phase (e.g. schema definitions assembled from multiple sibling scripts, where
     * building just the validated one would fail on definitions declared by its siblings).
     */
    public static ScriptValidationResult validate(Function<String, groovy.lang.Script> parse, String scriptText, String operation) {
        return validate(parse, () -> { }, scriptText, operation);
    }

    /**
     * Compiles {@code scriptText} via {@code parse}, and — unless {@code operation} is {@link
     * ScriptValidationRequest#SCRIPT_OPERATION_COMPILE} — also evaluates it and runs {@code
     * build}. Compile-only is used when the script body has not yet been reviewed by a user, so it
     * must not run.
     */
    public static ScriptValidationResult validate(
            Function<String, groovy.lang.Script> parse, Runnable build, String scriptText, String operation) {
        groovy.lang.Script script;
        try {
            script = parse.apply(scriptText);
        } catch (CompilationFailedException e) {
            return error(ScriptError.Phase.COMPILE, e);
        }
        if (ScriptValidationRequest.SCRIPT_OPERATION_COMPILE.equals(operation)) {
            return ScriptValidationResult.ok();
        }
        try {
            script.run();
        } catch (Exception e) {
            return error(ScriptError.Phase.EVALUATE, e);
        }
        try {
            build.run();
        } catch (Exception e) {
            return error(ScriptError.Phase.BUILD, e);
        }
        return ScriptValidationResult.ok();
    }

    /**
     * Runs {@code load} (parse and evaluate an already-deployed resource in one step) then {@code
     * build}, reporting a failure in either as an {@code evaluate}/{@code build}-phase error. Used
     * to re-check a deployed script against a throwaway target seeded with a candidate that isn't
     * itself the script being checked (e.g. checking operation scripts still build against a
     * candidate schema).
     */
    public static ScriptValidationResult validateResource(Runnable load, Runnable build) {
        try {
            load.run();
        } catch (Exception e) {
            return error(ScriptError.Phase.EVALUATE, e);
        }
        try {
            build.run();
        } catch (Exception e) {
            return error(ScriptError.Phase.BUILD, e);
        }
        return ScriptValidationResult.ok();
    }

    /** Runs each validation, merging the leaf errors of every one that failed. */
    public static ScriptValidationResult combine(List<Callable<ScriptValidationResult>> validations) {
        var errors = new ArrayList<ScriptError>();
        for (var validation : validations) {
            ScriptValidationResult result;
            try {
                result = validation.call();
            } catch (Exception e) {
                result = error(ScriptError.Phase.INITIALIZATION, e);
            }
            errors.addAll(result.errors());
        }
        return ScriptValidationResult.combined(errors);
    }

    /** Formats a validation failure at the given phase (e.g. {@link ScriptError.Phase#INITIALIZATION}). */
    public static ScriptValidationResult error(ScriptError.Phase phase, Exception e) {
        String message = e.getMessage() != null ? e.getMessage() : e.toString();
        Integer line = null;
        Integer column = null;
        String source = null;
        var syntaxError = firstSyntaxError(e);
        if (syntaxError != null) {
            line = syntaxError.getLine();
            column = syntaxError.getStartColumn();
        } else {
            var frame = scriptFrame(e);
            if (frame != null) {
                line = frame.getLineNumber();
                if (!frame.getFileName().matches("Script\\d+\\.groovy")) {
                    source = frame.getFileName();
                    message = frame.getFileName() + ": " + message;
                }
            }
        }
        return ScriptValidationResult.error(new ScriptError(phase, message, line, column, source));
    }

    private static SyntaxException firstSyntaxError(Exception e) {
        if (e instanceof MultipleCompilationErrorsException compilationErrors
                && compilationErrors.getErrorCollector().getErrorCount() > 0
                && compilationErrors.getErrorCollector().getError(0) instanceof SyntaxErrorMessage syntaxError) {
            return syntaxError.getCause();
        }
        return null;
    }

    private static StackTraceElement scriptFrame(Throwable e) {
        for (Throwable cause = e; cause != null; cause = cause.getCause()) {
            for (StackTraceElement element : cause.getStackTrace()) {
                if (element.getFileName() != null && element.getFileName().endsWith(".groovy")) {
                    return element;
                }
            }
        }
        return null;
    }
}
