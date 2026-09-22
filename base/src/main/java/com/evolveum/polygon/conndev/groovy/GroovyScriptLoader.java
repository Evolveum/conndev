/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.groovy;

import groovy.lang.Script;

/**
 * Loads Groovy/YAML operation-handler definitions into whatever builder a connector family binds
 * its scripting shell to (e.g. a REST or SQL operation-support builder) - common shape across
 * connector families whose handler builders otherwise share no ancestor.
 */
public interface GroovyScriptLoader {

    /** Loads and evaluates a script from a classpath resource. */
    void loadFromResource(String resource);

    /** Evaluates a script from a text string. */
    void loadFromString(String script);

    /** Parses (without evaluating) a script from a text string. */
    Script parse(String script);
}
