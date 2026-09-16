/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.yaml.decl;

import com.evolveum.polygon.conndev.groovy.GroovyScriptValidator;
import com.evolveum.polygon.conndev.groovy.ScriptError;
import com.evolveum.polygon.conndev.yaml.GroovyScriptCompiler;
import org.codehaus.groovy.control.CompilationFailedException;

import java.util.ArrayList;
import java.util.List;

/**
 * Checks the syntax of every Groovy fragment embedded in a YAML document — never binds onto a
 * builder, never runs a fragment's body. The YAML counterpart of a Groovy script's {@code
 * operation=="compile"} check.
 *
 * <p>Structural problems (unknown key, wrong shape) are silently skipped — that's {@link
 * DeclYamlBinder#bind}'s job, at build time.
 */
public final class GroovySyntaxChecker {

    private final LocatedDocument document;
    private final GroovyScriptCompiler compiler;
    private final List<ScriptError> errors = new ArrayList<>();

    private GroovySyntaxChecker(LocatedDocument document, GroovyScriptCompiler compiler) {
        this.document = document;
        this.compiler = compiler;
    }

    /** Checks every Groovy fragment reachable from {@code document} under {@code targetType}'s {@code @Yaml.*} shape. */
    public static List<ScriptError> check(LocatedDocument document, Class<?> targetType, GroovyScriptCompiler compiler) {
        var checker = new GroovySyntaxChecker(document, compiler);
        checker.walk(document.root(), targetType, "");
        return checker.errors;
    }

    /**
     * Checks every Groovy fragment under a schema document's {@code objectClasses:} envelope —
     * each entry binds onto a fresh {@code objectClassBuilderType} instance. {@code
     * relationships:} is not walked (unsupported today).
     */
    public static List<ScriptError> checkObjectClasses(
            LocatedDocument document, Class<?> objectClassBuilderType, GroovyScriptCompiler compiler) {
        var checker = new GroovySyntaxChecker(document, compiler);
        LocatedNode root = document.root();
        if (root.kind() != LocatedNode.Kind.OBJECT) {
            return checker.errors;
        }
        for (LocatedNode.Entry entry : root.entries()) {
            if ("objectClasses".equals(entry.key())) {
                checker.walkObjectClassesEntries(entry.value(), objectClassBuilderType);
            }
        }
        return checker.errors;
    }

    /**
     * Like {@link #checkObjectClasses}, plus a top-level {@code authentication:} block — REST's
     * envelope shape. SQL has no {@code authentication} concept, so it uses {@link
     * #checkObjectClasses} directly instead of this method.
     */
    public static List<ScriptError> checkOperations(
            LocatedDocument document, Class<?> objectClassBuilderType, Class<?> authenticationBuilderType,
            GroovyScriptCompiler compiler) {
        var checker = new GroovySyntaxChecker(document, compiler);
        LocatedNode root = document.root();
        if (root.kind() != LocatedNode.Kind.OBJECT) {
            return checker.errors;
        }
        for (LocatedNode.Entry entry : root.entries()) {
            switch (entry.key()) {
                case "objectClasses" -> checker.walkObjectClassesEntries(entry.value(), objectClassBuilderType);
                case "authentication" -> checker.walk(entry.value(), authenticationBuilderType, "authentication");
                default -> { }
            }
        }
        return checker.errors;
    }

    private void walkObjectClassesEntries(LocatedNode objectClasses, Class<?> objectClassBuilderType) {
        if (objectClasses == null || objectClasses.isNull() || objectClasses.kind() != LocatedNode.Kind.OBJECT) {
            return;
        }
        for (LocatedNode.Entry ocEntry : objectClasses.entries()) {
            walk(ocEntry.value(), objectClassBuilderType, "objectClasses." + ocEntry.key());
        }
    }

    /**
     * Checks one raw Groovy fragment's syntax — compiles it, never calls it. {@code path} names
     * it for the resulting {@link ScriptError#source()}.
     */
    public void checkFragment(LocatedNode value, String path) {
        if (value == null || value.isNull()) {
            return;
        }
        String text = value.text();
        if (text == null) {
            return;
        }
        try {
            compiler.compile(text);
        } catch (CompilationFailedException e) {
            errors.add(remap(e, text, value.line(), value.col(), path));
        }
    }

    /** Like {@link #check}, but over an explicit entry sub-selection — mirrors {@link DeclYamlBinder#bindEntries}. */
    public void checkFragments(List<LocatedNode.Entry> entries, Class<?> targetType, String path) {
        walkEntries(entries, targetType, path);
    }

    private void walk(LocatedNode node, Class<?> targetType, String path) {
        if (node == null || node.kind() != LocatedNode.Kind.OBJECT) {
            return;
        }
        walkEntries(node.entries(), targetType, path);
    }

    private void walkEntries(List<LocatedNode.Entry> entries, Class<?> targetType, String path) {
        var bindings = DeclYamlBinding.bindingsFor(targetType);
        for (LocatedNode.Entry entry : entries) {
            DeclYamlBinding binding = bindings.get(entry.key());
            if (binding == null) {
                continue;
            }
            String childPath = path.isEmpty() ? entry.key() : path + "." + entry.key();
            switch (binding) {
                case DeclYamlBinding.RuntimeScript ignored -> checkFragment(entry.value(), childPath);
                case DeclYamlBinding.Subtree st -> walk(entry.value(), st.method.type().returnType(), childPath);
                case DeclYamlBinding.MapBinding mb -> walkMap(entry.value(), mb.method.type().returnType(), childPath);
                case DeclYamlBinding.Custom c -> c.handler().checkGroovySyntax(entry.value(), childPath, this);
                case DeclYamlBinding.Property ignored -> { }
            }
        }
    }

    private void walkMap(LocatedNode node, Class<?> valueType, String path) {
        if (node == null || node.isNull() || node.kind() != LocatedNode.Kind.OBJECT) {
            return;
        }
        for (LocatedNode.Entry entry : node.entries()) {
            walk(entry.value(), valueType, path + "." + entry.key());
        }
    }

    /**
     * Maps a fragment-relative Groovy error back onto the real document.
     *
     * <p>Line: the compiled text is wrapped in one extra leading line, and an unterminated
     * construct gets reported past the fragment's real content — so the line is clamped to the
     * fragment's last real line rather than pointing past the document's end.
     *
     * <p>Column: block-scalar indentation is stripped before this class sees the text, so it's
     * re-measured from the raw source line. Only reported for a real block scalar ({@code key:
     * |}) with a non-clamped line — otherwise left {@code null} rather than guessed.
     */
    private ScriptError remap(CompilationFailedException e, String text, int baseLine, int baseCol, String source) {
        ScriptError raw = GroovyScriptValidator.error(ScriptError.Phase.COMPILE, e).errors().getFirst();
        if (raw.line() == null) {
            return new ScriptError(ScriptError.Phase.COMPILE, raw.message(), null, null, source);
        }
        // An inline (non-block) scalar puts its one real line of content on baseLine itself, not
        // after it — the block-only "content starts on the next document line" shift doesn't apply.
        boolean block = isBlockScalarIndicator(baseLine, baseCol);
        int fragmentLine = raw.line() - 1;
        int contentLines = contentLineCount(text);
        boolean clamped = fragmentLine > contentLines || fragmentLine < 1;
        int clampedFragmentLine = Math.max(1, Math.min(fragmentLine, contentLines));
        int line = block ? baseLine + clampedFragmentLine : baseLine;

        Integer column = null;
        if (block && !clamped && raw.column() != null) {
            String firstContentLine = document.rawLine(baseLine + 1);
            if (firstContentLine != null) {
                column = leadingWhitespace(firstContentLine) + raw.column();
            }
        }
        return new ScriptError(ScriptError.Phase.COMPILE, raw.message(), line, column, source);
    }

    private static int contentLineCount(String text) {
        int contentLines = (int) text.chars().filter(c -> c == '\n').count();
        if (!text.isEmpty() && !text.endsWith("\n")) {
            contentLines++;
        }
        return Math.max(contentLines, 1);
    }

    private boolean isBlockScalarIndicator(int line, int col) {
        String raw = document.rawLine(line);
        if (raw == null || col < 1 || col > raw.length()) {
            return false;
        }
        char c = raw.charAt(col - 1);
        return c == '|' || c == '>';
    }

    private static int leadingWhitespace(String line) {
        int i = 0;
        while (i < line.length() && (line.charAt(i) == ' ' || line.charAt(i) == '\t')) {
            i++;
        }
        return i;
    }
}
