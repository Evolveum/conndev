/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.devtools.yaml.scan;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Best-effort Javadoc extraction from the project's source roots, keyed by (declaring class,
 * method name, parameter count). Compiled classes do not carry Javadoc, so descriptions for the
 * generated artifacts come from the sources; a missing source or an ambiguous overload simply
 * yields no description (never a failure — descriptions are a nicety, not correctness).
 */
public final class JavadocReader {

    private static final Pattern JAVADOC = Pattern.compile("(?s)/\\*\\*(.*?)\\*/");

    private final List<Path> sourceRoots;
    private final Map<Class<?>, Map<String, String>> cache = new ConcurrentHashMap<>();

    public JavadocReader(List<Path> sourceRoots) {
        this.sourceRoots = List.copyOf(sourceRoots);
    }

    /** The cleaned first-paragraph Javadoc of the method, or {@code null}. */
    public String javadocOf(Class<?> declaringClass, String methodName, int paramCount) {
        Map<String, String> methods = cache.computeIfAbsent(declaringClass, this::parseClass);
        return methods.get(methodName + "/" + paramCount);
    }

    private Map<String, String> parseClass(Class<?> declaringClass) {
        Map<String, String> result = new LinkedHashMap<>();
        Path file = sourceFile(declaringClass);
        if (file == null || !Files.isRegularFile(file)) {
            return result;
        }
        String text;
        try {
            text = Files.readString(file);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read source " + file, e);
        }
        // Find every Javadoc block; the method it documents is the next method declaration after it
        // (past any annotations). Keyed by (name, paramCount); the first occurrence wins.
        for (Matcher javadoc = JAVADOC.matcher(text); javadoc.find();) {
            int from = javadoc.end();
            int methodStart = findMethodStart(text, from);
            if (methodStart < 0) {
                continue;
            }
            int openParen = indexOfParen(text, methodStart);
            if (openParen < 0) {
                continue;
            }
            int closeParen = matchParen(text, openParen);
            if (closeParen < 0) {
                continue;
            }
            int nameStart = openParen - 1;
            while (nameStart >= 0 && Character.isWhitespace(text.charAt(nameStart))) {
                nameStart--;
            }
            int nameEnd = nameStart + 1;
            while (nameStart >= 0 && isIdentifierChar(text.charAt(nameStart))) {
                nameStart--;
            }
            nameStart++;
            String name = text.substring(nameStart, nameEnd);
            int paramCount = countParams(text, openParen + 1, closeParen);
            String key = name + "/" + paramCount;
            result.putIfAbsent(key, clean(javadoc.group(1)));
        }
        return result;
    }

    /** Skips whitespace and annotation lines after the Javadoc to the start of the method name. */
    private static int findMethodStart(String text, int from) {
        int i = from;
        while (i < text.length()) {
            int lineEnd = text.indexOf('\n', i);
            String line = (lineEnd < 0 ? text.substring(i) : text.substring(i, lineEnd)).trim();
            if (line.isEmpty()) {
                i = lineEnd < 0 ? text.length() : lineEnd + 1;
                continue;
            }
            if (line.startsWith("@")) {
                i = lineEnd < 0 ? text.length() : lineEnd + 1;
                continue;
            }
            if (line.startsWith("/*") || line.startsWith("//")) {
                i = lineEnd < 0 ? text.length() : lineEnd + 1;
                continue;
            }
            int paren = line.indexOf('(');
            if (paren <= 0) {
                return -1; // no method declaration follows
            }
            return i + paren - 1; // position of the character before '('
        }
        return -1;
    }

    private static int indexOfParen(String text, int from) {
        int i = from;
        while (i < text.length() && Character.isWhitespace(text.charAt(i))) {
            i++;
        }
        return (i < text.length() && text.charAt(i) == '(') ? i : -1;
    }

    /** The index of the ')' matching the '(' at {@code open}. */
    private static int matchParen(String text, int open) {
        int depth = 0;
        for (int i = open; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '(' || c == '<' || c == '[') {
                depth++;
            } else if (c == ')' || c == '>' || c == ']') {
                depth--;
                if (depth == 0 && c == ')') {
                    return i;
                }
            }
        }
        return -1;
    }

    /** The parameter count of the (top-level) comma-separated parameter list. */
    private static int countParams(String text, int from, int end) {
        String params = text.substring(from, end).trim();
        if (params.isEmpty()) {
            return 0;
        }
        int count = 1;
        int depth = 0;
        for (int i = 0; i < params.length(); i++) {
            char c = params.charAt(i);
            if (c == '(' || c == '<' || c == '[') {
                depth++;
            } else if (c == ')' || c == '>' || c == ']') {
                depth--;
            } else if (c == ',' && depth == 0) {
                count++;
            }
        }
        return count;
    }

    /** Strips the {@code *} line prefixes, the tag sections and the inline markup. */
    private static String clean(String javadoc) {
        List<String> lines = new ArrayList<>();
        boolean done = false;
        for (String rawLine : javadoc.split("\n", -1)) {
            String line = rawLine.replaceFirst("^\\s*\\*+\\s?", "").replaceFirst("^\\s*\\*+$", "");
            if (line.isBlank()) {
                if (!lines.isEmpty()) {
                    break; // first paragraph only
                }
                continue;
            }
            if (line.startsWith("@")) {
                done = true;
                break;
            }
            lines.add(line);
        }
        if (done && lines.isEmpty()) {
            return null;
        }
        String text = String.join(" ", lines).trim();
        text = text.replaceAll("\\{@code\\s+([^}]+)}", "$1");
        text = text.replaceAll("\\{@link\\s+#?\\[?[^}\\s]+\\s*([^}]*)}", "$1");
        text = text.replaceAll("<\\/?code>", "");
        text = text.replaceAll("\\s+", " ").trim();
        return text.isEmpty() ? null : text;
    }

    private Path sourceFile(Class<?> clazz) {
        Class<?> topLevel = clazz;
        while (topLevel.getEnclosingClass() != null) {
            topLevel = topLevel.getEnclosingClass();
        }
        String relative = topLevel.getName().replace('.', '/') + ".java";
        for (Path root : sourceRoots) {
            Path candidate = root.resolve(relative);
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private static boolean isIdentifierChar(char c) {
        return Character.isJavaIdentifierPart(c);
    }
}
