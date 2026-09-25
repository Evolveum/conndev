/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.devtools.yaml.scan;

import java.util.ArrayList;
import java.util.List;

/**
 * The gaps and warnings of one generation run. Errors mean the generated artifacts would be
 * wrong (an unresolvable builder type, a {@code @Yaml.Custom} handler with no declared shape, a
 * shape merge conflict) and fail the build when {@code failOnGaps} is on; warnings are
 * best-effort degradations (an unmapped leaf type that fell back to string, missing Javadoc).
 */
public final class GapReport {

    private final List<String> errors = new ArrayList<>();
    private final List<String> warnings = new ArrayList<>();

    public void error(String message) {
        errors.add(message);
    }

    public void warn(String message) {
        if (!warnings.contains(message)) {
            warnings.add(message);
        }
    }

    public List<String> errors() {
        return List.copyOf(errors);
    }

    public List<String> warnings() {
        return List.copyOf(warnings);
    }

    public boolean hasErrors() {
        return !errors.isEmpty();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (String error : errors) {
            sb.append("ERROR: ").append(error).append('\n');
        }
        for (String warning : warnings) {
            sb.append("WARNING: ").append(warning).append('\n');
        }
        return sb.toString();
    }
}
