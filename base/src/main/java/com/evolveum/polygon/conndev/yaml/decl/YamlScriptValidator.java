/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.yaml.decl;

import com.evolveum.polygon.conndev.concepts.CheckedRunnable;
import com.evolveum.polygon.conndev.groovy.GroovyScriptValidator;
import com.evolveum.polygon.conndev.groovy.ScriptError;
import com.evolveum.polygon.conndev.groovy.ScriptValidationRequest;
import com.evolveum.polygon.conndev.groovy.ScriptValidationResult;

import java.util.List;
import java.util.function.Function;

/**
 * YAML counterpart of {@link GroovyScriptValidator#validate}: {@code compile} never binds or
 * executes anything, {@code build} does. Every YAML {@code validateScript} branch (schema and
 * operations, REST and SQL) reduces to this skeleton — only which builder/loader gets
 * constructed, and how siblings get reloaded, differs per caller.
 */
public final class YamlScriptValidator {

    private YamlScriptValidator() {
    }

    /**
     * @param request       the candidate request
     * @param syntaxCheck   checks embedded Groovy fragments' syntax; never touches a builder, runs
     *                      for every {@code operation}
     * @param loadCandidate binds the candidate onto a throwaway builder already seeded with
     *                      siblings; only runs for {@code operation=="build"}
     * @param build         finalizes the builder; only runs after {@code loadCandidate} succeeds
     */
    public static ScriptValidationResult validate(
            ScriptValidationRequest request,
            Function<LocatedDocument, List<ScriptError>> syntaxCheck,
            CheckedRunnable<Exception> loadCandidate,
            CheckedRunnable<Exception> build) {
        LocatedDocument document;
        try {
            document = LocatedDocument.parse(sourceName(request), request.scriptText());
        } catch (IllegalArgumentException e) {
            return GroovyScriptValidator.error(ScriptError.Phase.COMPILE, e);
        }
        var syntaxErrors = syntaxCheck.apply(document);
        if (!syntaxErrors.isEmpty() || ScriptValidationRequest.SCRIPT_OPERATION_COMPILE.equals(request.operation())) {
            return ScriptValidationResult.combined(syntaxErrors);
        }
        try {
            loadCandidate.run();
        } catch (Exception e) {
            return GroovyScriptValidator.error(ScriptError.Phase.EVALUATE, e);
        }
        try {
            build.run();
        } catch (Exception e) {
            return GroovyScriptValidator.error(ScriptError.Phase.BUILD, e);
        }
        return ScriptValidationResult.ok();
    }

    private static String sourceName(ScriptValidationRequest request) {
        return request.filename() != null ? request.filename() : "candidate.yaml";
    }
}
