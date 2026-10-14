/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.groovy;


import groovy.lang.GroovyClassLoader;
import groovy.lang.GroovyShell;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.identityconnectors.framework.common.exceptions.ConfigurationException;

public class GroovyContext {

    /** Message used everywhere a Groovy context is rejected because Groovy is disabled. */
    public static final String GROOVY_DISABLED_MESSAGE =
            "Groovy is disabled by configuration (groovyDisabled=true); Groovy scripts cannot be loaded";

    private final CompilerConfiguration compilerConfiguration;
    private final GroovyClassLoader groovyClassLoader;
    private final boolean groovyDisabled;

    public GroovyContext() {
        this(false);
    }

    public GroovyContext(boolean groovyDisabled) {
        this.groovyDisabled = groovyDisabled;
        compilerConfiguration = new CompilerConfiguration();
        groovyClassLoader = new GroovyClassLoader(this.getClass().getClassLoader());
    }

    public GroovyShell createShell() {
        if (groovyDisabled) {
            throw new ConfigurationException(GROOVY_DISABLED_MESSAGE);
        }
        return  new GroovyShell(groovyClassLoader, compilerConfiguration);
    }


}
