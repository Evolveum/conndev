/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.yaml;

import com.evolveum.polygon.conndev.build.api.NormalizationBuilder;
import com.evolveum.polygon.conndev.build.api.SearchOperationBuilder;
import com.evolveum.polygon.conndev.yaml.model.YamlNormalize;

/**
 * Maps the typed {@code normalize} section onto {@link NormalizationBuilder} (closure-free
 * {@code normalize()} accessor): {@code toSingleValue} is a plain attribute name, the
 * {@code rewrite*}/{@code restore*} fields are Groovy blocks compiled to closures.
 */
public final class YamlNormalizeHandler {

    private final GroovyScriptCompiler scriptCompiler;

    public YamlNormalizeHandler(GroovyScriptCompiler scriptCompiler) {
        this.scriptCompiler = scriptCompiler;
    }

    public void load(SearchOperationBuilder searchBuilder, YamlNormalize normalize) {
        if (normalize == null) {
            return;
        }
        NormalizationBuilder nb = searchBuilder.normalize();
        if (normalize.toSingleValue != null) {
            nb.toSingleValue(normalize.toSingleValue);
        }
        if (normalize.rewriteUid != null) {
            nb.rewriteUid(scriptCompiler.compile(normalize.rewriteUid));
        }
        if (normalize.rewriteName != null) {
            nb.rewriteName(scriptCompiler.compile(normalize.rewriteName));
        }
        if (normalize.restoreUid != null) {
            nb.restoreUid(scriptCompiler.compile(normalize.restoreUid));
        }
        if (normalize.restoreName != null) {
            nb.restoreName(scriptCompiler.compile(normalize.restoreName));
        }
    }
}
