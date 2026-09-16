/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.groovy;

import org.identityconnectors.framework.common.objects.ScriptContext;
import org.identityconnectors.framework.spi.operations.ScriptOnResourceOp;

/**
 * A {@link ScriptOnResourceOp} request to validate a candidate connector development artifact:
 * which kind it is ({@code artifactKind}, e.g. {@link #ARTIFACT_KIND_SCHEMA}), which
 * already-deployed resource it would replace ({@code filename}, if any), its candidate content,
 * which {@code language} ({@link #LANGUAGE_GROOVY} or {@link #LANGUAGE_YAML}) it is written in, and
 * whether to only {@link #SCRIPT_OPERATION_COMPILE compile} it or also {@link
 * #SCRIPT_OPERATION_BUILD build} it.
 */
public record ScriptValidationRequest(String artifactKind, String filename, String scriptText, String operation, String language) {

    public static final String SCRIPT_ARGUMENT_OPERATION = "operation";
    public static final String SCRIPT_OPERATION_BUILD = "build";
    public static final String SCRIPT_OPERATION_COMPILE = "compile";
    public static final String SCRIPT_ARGUMENT_ARTIFACT_KIND = "artifactKind";
    public static final String ARTIFACT_KIND_SCHEMA = "schema";
    public static final String SCRIPT_ARGUMENT_FILENAME = "filename";
    public static final String LANGUAGE_GROOVY = "groovy";
    public static final String LANGUAGE_YAML = "yaml";

    public static ScriptValidationRequest from(ScriptContext request) {
        var arguments = request.getScriptArguments();
        return new ScriptValidationRequest(
                (String) arguments.get(SCRIPT_ARGUMENT_ARTIFACT_KIND),
                (String) arguments.get(SCRIPT_ARGUMENT_FILENAME),
                request.getScriptText(),
                (String) arguments.get(SCRIPT_ARGUMENT_OPERATION),
                request.getScriptLanguage());
    }

    /** {@link #language()}, case-insensitively compared to {@link #LANGUAGE_YAML}. */
    public boolean isYaml() {
        return LANGUAGE_YAML.equalsIgnoreCase(language);
    }
}
