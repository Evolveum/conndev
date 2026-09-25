/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.devtools.yaml.scan;

import java.lang.annotation.Annotation;

/**
 * The fully-qualified names of the conndev binding markers, resolved by name rather than by class
 * reference. The scanner must work on classes loaded from an arbitrary caller classloader (the
 * project's build classpath in the Maven plugin), and a class-reference comparison would fail
 * whenever the marker was loaded by a different classloader than the one under scan — matching
 * by name is immune to that.
 */
final class TypeNames {

    static final String YAML_ANNOTATION = "com.evolveum.polygon.conndev.annotations.Yaml";
    static final String YAML_KEY = YAML_ANNOTATION + "$Key";
    static final String YAML_SUB = YAML_ANNOTATION + "$Sub";
    static final String YAML_CUSTOM = YAML_ANNOTATION + "$Custom";
    static final String YAML_MAP = YAML_ANNOTATION + "$Map";
    static final String YAML_PATH = YAML_ANNOTATION + "$Path";
    static final String YAML_VALUE_PARSER = YAML_ANNOTATION + "$ValueParser";
    static final String SCRIPT_RUNTIME = "com.evolveum.polygon.conndev.annotations.Script$Runtime";
    static final String CLOSURE = "groovy.lang.Closure";
    static final String ATTRIBUTE_PATH_DECLARATION = "com.evolveum.polygon.conndev.api.AttributePathDeclaration";
    static final String DEPRECATED = "java.lang.Deprecated";

    private TypeNames() {
    }

    /** The annotation of the given fully-qualified type on the element, or {@code null}. */
    static Annotation annotation(java.lang.reflect.AnnotatedElement element, String fqn) {
        for (Annotation annotation : element.getAnnotations()) {
            if (annotation.annotationType().getName().equals(fqn)) {
                return annotation;
            }
        }
        return null;
    }

    /** The {@code value()} member of a single-member annotation, as text ({@code null} if absent). */
    static String stringValue(Annotation annotation) {
        try {
            Object value = annotation.annotationType().getMethod("value").invoke(annotation);
            return value instanceof String text ? text : (value == null ? null : String.valueOf(value));
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot read 'value' of " + annotation.annotationType().getName(), e);
        }
    }

    /** The {@code value()} member of a single-member annotation, as a class name ({@code null} if absent). */
    static String classValue(Annotation annotation) {
        try {
            Object value = annotation.annotationType().getMethod("value").invoke(annotation);
            return value instanceof Class<?> clazz ? clazz.getName() : null;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot read 'value' of " + annotation.annotationType().getName(), e);
        }
    }
}
