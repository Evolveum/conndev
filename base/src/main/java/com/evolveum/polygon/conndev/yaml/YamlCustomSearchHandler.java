/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.yaml;

import com.evolveum.polygon.conndev.api.FilterSpecification;
import com.evolveum.polygon.conndev.build.api.SearchOperationBuilder;
import com.evolveum.polygon.conndev.build.api.SearchScriptBuilder;
import com.evolveum.polygon.conndev.yaml.model.YamlCustom;
import com.evolveum.polygon.conndev.yaml.model.YamlSupportedFilter;

/**
 * Maps the typed {@code custom} (fully scripted) search via the closure-free {@code custom()} accessor.
 * Its {@code supportedFilters} carry only a {@code spec} (evaluated against the {@link SearchScriptBuilder});
 * {@code implementation} supplies the scripted search body.
 */
public final class YamlCustomSearchHandler {

    private final GroovyScriptCompiler scriptCompiler;

    public YamlCustomSearchHandler(GroovyScriptCompiler scriptCompiler) {
        this.scriptCompiler = scriptCompiler;
    }

    public void load(SearchOperationBuilder searchBuilder, YamlCustom custom) {
        if (custom == null) {
            return;
        }
        SearchScriptBuilder sb = searchBuilder.custom();
        if (Boolean.TRUE.equals(custom.emptyFilterSupported)) {
            sb.emptyFilterSupported(true);
        }
        if (custom.supportedFilters != null) {
            for (YamlSupportedFilter filter : custom.supportedFilters) {
                sb.supportedFilter((FilterSpecification) scriptCompiler.evaluate(filter.spec, sb));
            }
        }
        if (custom.implementation != null) {
            sb.implementation(scriptCompiler.compile(custom.implementation));
        }
    }
}
