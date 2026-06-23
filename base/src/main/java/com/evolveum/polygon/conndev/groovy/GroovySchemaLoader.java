/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.groovy;

import com.evolveum.polygon.conndev.schema.BaseSchema;
import com.evolveum.polygon.conndev.schema.BaseSchemaBuilder;
import groovy.lang.GroovyShell;
import org.codehaus.groovy.runtime.MethodClosure;

import java.io.InputStreamReader;

public class GroovySchemaLoader {

    private GroovyShell shell;
    BaseSchemaBuilder schemaBuilder;

    public GroovySchemaLoader(GroovyContext context, BaseSchemaBuilder schemaBuilder) {
        shell = context.createShell();
        this.schemaBuilder = schemaBuilder;
        shell.setVariable("objectClass", new MethodClosure(schemaBuilder, "objectClass"));
        shell.setVariable("relationship", new MethodClosure(schemaBuilder, "relationship"));

    }

    public void load(String groovyScript) {
        shell.evaluate(groovyScript);
    }

    public BaseSchema build() {
        return schemaBuilder.build();
    }

    public void loadFromResource(String s) {
        shell.evaluate(new InputStreamReader(this.getClass().getResourceAsStream(s)), s);
    }
}
