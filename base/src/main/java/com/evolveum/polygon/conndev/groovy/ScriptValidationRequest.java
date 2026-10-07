/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.groovy;

import org.identityconnectors.framework.common.objects.ScriptContext;
import org.identityconnectors.framework.spi.operations.ScriptOnResourceOp;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A {@link ScriptOnResourceOp} request to validate a candidate connector development artifact:
 * which kind it is ({@code artifactKind}, e.g. {@link #ARTIFACT_KIND_SCHEMA}), which
 * already-deployed resource it would replace ({@code filename}, if any), its candidate content,
 * which {@code language} ({@link #LANGUAGE_GROOVY} or {@link #LANGUAGE_YAML}) it is written in,
 * whether to only {@link #SCRIPT_OPERATION_COMPILE compile} it or also {@link
 * #SCRIPT_OPERATION_BUILD build} it, and any additional {@code overrides} (filename -> candidate
 * content) for validating several not-yet-saved replacements together, as one batch.
 */
public record ScriptValidationRequest(
        String artifactKind, String filename, String scriptText, String operation, String language,
        Map<String, String> overrides) {

    public static final String SCRIPT_ARGUMENT_OPERATION = "operation";
    public static final String SCRIPT_OPERATION_BUILD = "build";
    public static final String SCRIPT_OPERATION_COMPILE = "compile";
    public static final String SCRIPT_ARGUMENT_ARTIFACT_KIND = "artifactKind";
    public static final String ARTIFACT_KIND_SCHEMA = "schema";
    public static final String SCRIPT_ARGUMENT_FILENAME = "filename";
    public static final String SCRIPT_ARGUMENT_OVERRIDES = "overrides";
    public static final String LANGUAGE_GROOVY = "groovy";
    public static final String LANGUAGE_YAML = "yaml";

    @SuppressWarnings("unchecked")
    public static ScriptValidationRequest from(ScriptContext request) {
        var arguments = request.getScriptArguments();
        var overridesArgument = arguments.get(SCRIPT_ARGUMENT_OVERRIDES);
        var overrides = overridesArgument instanceof Map<?, ?> map
                ? (Map<String, String>) map
                : Map.<String, String>of();
        return new ScriptValidationRequest(
                (String) arguments.get(SCRIPT_ARGUMENT_ARTIFACT_KIND),
                (String) arguments.get(SCRIPT_ARGUMENT_FILENAME),
                request.getScriptText(),
                (String) arguments.get(SCRIPT_ARGUMENT_OPERATION),
                request.getScriptLanguage(),
                overrides);
    }

    /** {@link #language()}, case-insensitively compared to {@link #LANGUAGE_YAML}. */
    public boolean isYaml() {
        return LANGUAGE_YAML.equalsIgnoreCase(language);
    }

    /**
     * Every filename this request substitutes with in-memory content - the primary {@link
     * #filename()}/{@link #scriptText()} pair (if any) plus {@link #overrides()} - as one unified
     * map, so a caller doesn't have to handle the primary pair and the extra overrides separately.
     */
    public Map<String, String> allOverrides() {
        if (filename == null) {
            return overrides;
        }
        var all = new LinkedHashMap<>(overrides);
        all.put(filename, scriptText);
        return all;
    }
}
