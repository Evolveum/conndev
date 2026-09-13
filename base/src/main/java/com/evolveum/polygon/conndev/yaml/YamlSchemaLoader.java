/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.yaml;

import com.evolveum.polygon.conndev.groovy.GroovyContext;
import com.evolveum.polygon.conndev.schema.BaseSchema;
import com.evolveum.polygon.conndev.schema.BaseSchemaBuilder;
import com.evolveum.polygon.conndev.yaml.decl.DeclYamlBinder;
import com.evolveum.polygon.conndev.yaml.decl.LocatedDocument;
import com.evolveum.polygon.conndev.yaml.decl.LocatedNode;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;

/**
 * Declarative YAML front-end of the schema DSL — the YAML counterpart of
 * {@link com.evolveum.polygon.conndev.groovy.GroovySchemaLoader}. A document is parsed into a
 * location-aware tree ({@link LocatedDocument}) and bound by the generic {@link DeclYamlBinder} onto the
 * same {@link BaseSchemaBuilder} the Groovy DSL drives, so both front-ends can be used side by side
 * and a connector can migrate its definitions file by file.
 *
 * <p>The document uses the extended shape: a plural {@code objectClasses} mapping (one entry per
 * object class) plus an optional top-level {@code relationships} mapping. Documents naming the same
 * object class merge into one builder ({@code objectClass(name)} is a {@code computeIfAbsent}), so a
 * native definition and a ConnId overlay can stay in separate files, like in Groovy. Only one
 * document per file is allowed.
 */
public class YamlSchemaLoader {

    private final BaseSchemaBuilder schemaBuilder;
    private final GroovyScriptCompiler compiler;

    public YamlSchemaLoader(BaseSchemaBuilder schemaBuilder) {
        this.schemaBuilder = schemaBuilder;
        this.compiler = new GroovyScriptCompiler(new GroovyContext());
    }

    public void load(String yaml) {
        loadDocument(LocatedDocument.parse("inline document", yaml));
    }

    public void load(Reader reader, String sourceName) {
        loadDocument(LocatedDocument.parse(sourceName, reader));
    }

    public void loadFromResource(String resource) {
        InputStream stream = getClass().getResourceAsStream(resource);
        if (stream == null) {
            throw new IllegalArgumentException("YAML resource not found: " + resource);
        }
        try (Reader reader = new InputStreamReader(stream)) {
            loadDocument(LocatedDocument.parse(resource, reader));
        } catch (IOException e) {
            throw new IllegalArgumentException("Could not read YAML resource (" + resource + "): " + e.getMessage(), e);
        }
    }

    public BaseSchema build() {
        return schemaBuilder.build();
    }

    private void loadDocument(LocatedDocument document) {
        LocatedNode root = document.root();
        if (root.kind() != LocatedNode.Kind.OBJECT) {
            throw new IllegalArgumentException("YAML schema document must be a mapping (" + document.sourceName() + ")");
        }
        DeclYamlBinder binder = new DeclYamlBinder(document, compiler);
        boolean hasObjectClasses = false;
        for (LocatedNode.Entry entry : root.entries()) {
            switch (entry.key()) {
                case "objectClasses" -> {
                    hasObjectClasses = true;
                    applyObjectClasses(binder, entry.value());
                }
                case "relationships" -> applyRelationships(binder, entry.value());
                default -> throw unknownTopLevelKey(document, entry);
            }
        }
        if (!hasObjectClasses) {
            throw new IllegalArgumentException("YAML schema document is missing the 'objectClasses' section ("
                    + document.sourceName() + ")");
        }
    }

    private void applyObjectClasses(DeclYamlBinder binder, LocatedNode node) {
        for (LocatedNode.Entry entry : requireMap(node, "objectClasses").entries()) {
            binder.bind(entry.value(), schemaBuilder.objectClass(entry.key()));
        }
    }

    private void applyRelationships(DeclYamlBinder binder, LocatedNode node) {
        // The base relationship builder is not yet implemented (the concrete subject/object
        // participants live in the protocol connectors), so a YAML relationships block is rejected
        // with a clear message rather than silently dropped.
        throw new IllegalArgumentException("The 'relationships' block is not yet supported by the base "
                + "schema builder; define relationships in the protocol connector's schema");
    }

    private static LocatedNode requireMap(LocatedNode node, String key) {
        // A null/omitted block binds nothing: a scalar (null) node has no entries to iterate.
        if (node == null || node.isNull()) {
            return node;
        }
        if (node.kind() != LocatedNode.Kind.OBJECT) {
            throw new IllegalArgumentException("'" + key + "' must be a mapping but found a " + node.kind()
                    + " at " + node.line() + ":" + node.col());
        }
        return node;
    }

    private static IllegalArgumentException unknownTopLevelKey(LocatedDocument document, LocatedNode.Entry entry) {
        return new IllegalArgumentException("Unknown top-level key '" + entry.key() + "' in YAML schema document ("
                + document.sourceName() + ":" + entry.keyLine() + ":" + entry.keyCol() + ")");
    }
}
