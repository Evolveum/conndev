/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.devtools.yaml.scan;

import com.evolveum.polygon.conndev.devtools.yaml.config.ShapeFileConfig;
import com.evolveum.polygon.conndev.devtools.yaml.shape.AnyShape;
import com.evolveum.polygon.conndev.devtools.yaml.shape.ObjectShape;
import com.evolveum.polygon.conndev.devtools.yaml.shape.PathShape;
import com.evolveum.polygon.conndev.devtools.yaml.shape.ScriptShape;
import com.evolveum.polygon.conndev.devtools.yaml.shape.YamlProperty;
import com.evolveum.polygon.conndev.devtools.yaml.shape.YamlShape;
import com.evolveum.polygon.conndev.devtools.yaml.shape.MapShape;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/**
 * The static, non-executing counterpart of the runtime binder's scan
 * ({@code DeclYamlBinding.scan}): given a builder type, it derives the YAML keys the type accepts
 * and their shapes, with the exact same binding rules the runtime applies — the five
 * {@code @Yaml.*} markers, the {@code @Script.Runtime} single-{@code Closure} bindings, the
 * key-resolution and shadowing rules, the DefinitionValue-overload insensitivity and the full
 * hierarchy walk.
 *
 * <p>Everything is resolved reflectively through the caller's classloader and reduced to
 * name strings immediately, so no scanned class or annotation leaks into the shape model
 * (classloader isolation).
 */
public final class BindingScanner {

    private final ClassLoader loader;
    private final JavadocReader javadoc;
    private final HandlerShapes handlers;
    private final ShapeFileConfig config;
    private final GapReport report;
    private final Map<Class<?>, Map<String, YamlProperty>> cache = new ConcurrentHashMap<>();
    private final Set<Class<?>> inFlight = ConcurrentHashMap.newKeySet();

    public BindingScanner(ClassLoader loader, JavadocReader javadoc, HandlerShapes handlers,
                          ShapeFileConfig config, GapReport report) {
        this.loader = loader;
        this.javadoc = javadoc;
        this.handlers = handlers;
        this.config = config;
        this.report = report;
    }

    /** The bindings of {@code type}, scanned and cached once per class. */
    public Map<String, YamlProperty> bindingsOf(Class<?> type) {
        Map<String, YamlProperty> cached = cache.get(type);
        if (cached != null) {
            return cached;
        }
        // A re-entrant scan of the same class is a binding cycle (a sub/map value type is the type
        // being scanned) — report it as a gap instead of recursing forever.
        if (!inFlight.add(type)) {
            report.error("Binding cycle on " + type.getName() + " (a sub or map value type resolves back to it); "
                    + "declare the shape explicitly in the shape file");
            return Map.of();
        }
        try {
            Map<String, YamlProperty> scanned = scan(type);
            cache.put(type, scanned);
            return scanned;
        } finally {
            inFlight.remove(type);
        }
    }

    private Map<String, YamlProperty> scan(Class<?> type) {
        Map<String, YamlProperty> map = new LinkedHashMap<>();
        for (Method method : bindingMethods(type)) {
            YamlProperty property = buildBinding(type, method);
            if (property != null) {
                map.put(property.key(), property);
            }
        }
        return map;
    }

    /** The object shape of {@code type} — its {@link #bindingsOf} as a closed mapping. */
    public ObjectShape shapeOf(Class<?> type) {
        ObjectShape shape = ObjectShape.of(type.getName());
        for (YamlProperty property : bindingsOf(type).values()) {
            shape.properties().put(property.key(), property);
        }
        return shape;
    }

    /**
     * The binding methods: the {@code @Yaml.*}-marked ones plus the {@code @Script.Runtime}
     * single-argument {@code Closure} methods whose key is not already occupied by a
     * {@code @Yaml.*} binding (a Groovy convenience like {@code connId(Closure)} must not shadow a
     * {@code @Yaml.Sub connId()} accessor under the same key) — the same selection the runtime
     * binder makes.
     */
    private List<Method> bindingMethods(Class<?> type) {
        Map<String, Method> yamlBySignature = new LinkedHashMap<>();
        collectWhere(type, BindingScanner::hasYamlMarker, yamlBySignature);

        Set<String> yamlKeys = new HashSet<>();
        for (Method method : yamlBySignature.values()) {
            yamlKeys.add(bindingKey(method));
        }

        Map<String, Method> bySignature = new LinkedHashMap<>(yamlBySignature);
        collectWhere(type, method -> isScriptedClosure(method) && !yamlKeys.contains(method.getName()), bySignature);
        return new ArrayList<>(bySignature.values());
    }

    private static boolean hasYamlMarker(Method method) {
        return TypeNames.annotation(method, TypeNames.YAML_KEY) != null
                || TypeNames.annotation(method, TypeNames.YAML_SUB) != null
                || TypeNames.annotation(method, TypeNames.YAML_CUSTOM) != null
                || TypeNames.annotation(method, TypeNames.YAML_MAP) != null
                || TypeNames.annotation(method, TypeNames.YAML_PATH) != null;
    }

    /** A single-argument method is a block-scalar-to-closure binding when its {@code Closure} parameter carries {@code @Script.Runtime}. */
    private static boolean isScriptedClosure(Method method) {
        return findScriptedClosureParam(method) != null;
    }

    /** The YAML key a binding method binds: the {@code @Yaml.Key} value, else the method name. */
    private static String bindingKey(Method method) {
        Annotation key = TypeNames.annotation(method, TypeNames.YAML_KEY);
        String value = key == null ? null : TypeNames.stringValue(key);
        return (value != null && !value.isEmpty()) ? value : method.getName();
    }

    /** The {@code @Script.Runtime} {@code Closure} parameter of a single-argument method, or {@code null}. */
    private static Parameter findScriptedClosureParam(Method method) {
        if (method.getParameterCount() != 1) {
            return null;
        }
        Parameter param = method.getParameters()[0];
        if (!TypeNames.CLOSURE.equals(param.getType().getName())) {
            return null;
        }
        return TypeNames.annotation(param, TypeNames.SCRIPT_RUNTIME) != null ? param : null;
    }

    /** Walks the class and its full hierarchy, collecting the methods matching {@code predicate} (declared, most-derived first). */
    private static void collectWhere(Class<?> type, Predicate<Method> predicate, Map<String, Method> bySignature) {
        for (Method method : sortedDeclared(type)) {
            if (predicate.test(method)) {
                bySignature.putIfAbsent(signature(method), method);
            }
        }
        if (type.isInterface()) {
            for (Class<?> superInterface : sortedInterfaces(type)) {
                collectWhere(superInterface, predicate, bySignature);
            }
        } else {
            Class<?> superClass = type.getSuperclass();
            if (superClass != null && superClass != Object.class) {
                collectWhere(superClass, predicate, bySignature);
            }
            for (Class<?> interfaceType : sortedInterfaces(type)) {
                collectWhere(interfaceType, predicate, bySignature);
            }
        }
    }

    private static Method[] sortedDeclared(Class<?> type) {
        Method[] methods = type.getDeclaredMethods();
        Arrays.sort(methods, Comparator.comparing(Method::getName).thenComparing(m -> signature(m)));
        return methods;
    }

    private static List<Class<?>> sortedInterfaces(Class<?> type) {
        List<Class<?>> interfaces = new ArrayList<>(Arrays.asList(type.getInterfaces()));
        interfaces.sort(Comparator.comparing(Class::getName));
        return interfaces;
    }

    private static String signature(Method method) {
        return method.getName() + "(" + Arrays.toString(method.getParameterTypes()) + ")";
    }

    private YamlProperty buildBinding(Class<?> targetClass, Method method) {
        Annotation keyAnn = TypeNames.annotation(method, TypeNames.YAML_KEY);
        Annotation subAnn = TypeNames.annotation(method, TypeNames.YAML_SUB);
        Annotation customAnn = TypeNames.annotation(method, TypeNames.YAML_CUSTOM);
        Annotation mapAnn = TypeNames.annotation(method, TypeNames.YAML_MAP);
        Annotation pathAnn = TypeNames.annotation(method, TypeNames.YAML_PATH);
        Annotation parserAnn = TypeNames.annotation(method, TypeNames.YAML_VALUE_PARSER);
        Parameter closureParam = findScriptedClosureParam(method);

        String keyAnnValue = keyAnn == null ? null : TypeNames.stringValue(keyAnn);
        String bindingKey = (keyAnnValue != null && !keyAnnValue.isEmpty()) ? keyAnnValue : method.getName();
        boolean deprecated = method.isAnnotationPresent(java.lang.Deprecated.class);
        String source = targetClass.getName() + "#" + method.getName();
        String description = javadoc.javadocOf(method.getDeclaringClass(), method.getName(), method.getParameterCount());

        if (subAnn != null) {
            Class<?> subType = returnType(targetClass, method, source);
            if (subType == null) {
                return null;
            }
            if (subType == targetClass) {
                return selfReferential(bindingKey, "@Yaml.Sub", source, description, deprecated);
            }
            return property(bindingKey, shapeOf(subType), description, deprecated, source);
        }
        if (closureParam != null) {
            return property(bindingKey, new ScriptShape(false, false, source, description, deprecated),
                    description, deprecated, source);
        }
        if (customAnn != null) {
            String handlerFqn = TypeNames.classValue(customAnn);
            var shape = handlers.resolve(handlerFqn, targetClass);
            if (shape.isEmpty()) {
                return null;
            }
            return property(bindingKey, shape.get(), description, deprecated, source);
        }
        if (mapAnn != null) {
            String yamlKey = TypeNames.stringValue(mapAnn);
            if (yamlKey == null || yamlKey.isEmpty()) {
                report.error("@Yaml.Map on " + source + " needs a block key");
                return null;
            }
            Class<?> valueType = returnType(targetClass, method, source);
            if (valueType == null) {
                return null;
            }
            if (valueType == targetClass) {
                return selfReferential(yamlKey, "@Yaml.Map", source, description, deprecated);
            }
            return property(yamlKey, new MapShape(shapeOf(valueType), List.of(), source, description, deprecated),
                    description, deprecated, source);
        }
        if (keyAnn != null || pathAnn != null) {
            Class<?>[] params = method.getParameterTypes();
            if (params.length != 1) {
                report.error((pathAnn != null ? "@Yaml.Path" : "@Yaml.Key") + " method " + source
                        + " must take exactly one parameter, found " + params.length);
                return null;
            }
            YamlShape leaf;
            if (pathAnn != null) {
                if (!TypeNames.ATTRIBUTE_PATH_DECLARATION.equals(params[0].getName())) {
                    report.error("@Yaml.Path method " + source + " must take a single AttributePathDeclaration parameter, found "
                            + params[0].getName());
                    return null;
                }
                leaf = new PathShape(source, description, deprecated);
            } else {
                String parserFqn = parserAnn == null ? null : TypeNames.classValue(parserAnn);
                YamlShape declared = parserFqn == null ? null : config.valueParserShape(parserFqn);
                if (parserFqn != null && declared == null) {
                    report.warn("@Yaml.ValueParser target " + parserFqn + " (at " + source + ") has no declared shape; "
                            + "falling back to the inferred leaf. Declare it in the shape file's valueParsers.");
                }
                leaf = TypeMapper.leaf(params[0], parserFqn, declared, source, description, deprecated, report);
            }
            return property(bindingKey, leaf, description, deprecated, source);
        }
        return null;
    }

    /**
     * The public method matching the annotated method's signature, with its generic return type
     * resolved against {@code targetClass} (the CRTP generics collapse to the concrete sub-builder
     * types). Returns {@code null} — after reporting a gap — when unresolvable.
     *
     * <p>{@code getMethods()} returns one {@link Method} per distinct generic return type for the
     * same erasure (a builder implementing several interfaces with covariant returns yields a
     * variant per interface); the most specific variant names the concrete sub-builder.
     */
    private Class<?> returnType(Class<?> targetClass, Method annotated, String source) {
        Class<?>[] params = annotated.getParameterTypes();
        List<Method> candidates = new ArrayList<>();
        for (Method method : targetClass.getMethods()) {
            if (method.getName().equals(annotated.getName()) && Arrays.equals(method.getParameterTypes(), params)) {
                candidates.add(method);
            }
        }
        Method resolved = candidates.isEmpty() ? annotated : mostSpecific(candidates);
        Type genericReturn = resolved.getGenericReturnType();
        Class<?> raw = TypeResolver.resolveRaw(genericReturn, targetClass);
        if (raw == null) {
            report.error("Cannot resolve the return type of " + source + " (" + genericReturn + "); "
                    + "declare the shape explicitly in the shape file");
            return null;
        }
        if (!isLoadable(raw)) {
            report.error("Cannot load " + raw.getName() + " (return type of " + source + ")");
            return null;
        }
        return raw;
    }

    /**
     * The candidate whose (raw) return type is a subtype of every other candidate's — the
     * concrete sub-builder among the interface/abstract variants. Falls back to the first
     * non-abstract return type, then to the first candidate, when nothing is strictly most
     * specific.
     */
    private static Method mostSpecific(List<Method> candidates) {
        for (Method candidate : candidates) {
            if (isMostSpecific(candidate, candidates)) {
                return candidate;
            }
        }
        for (Method candidate : candidates) {
            Class<?> raw = rawType(candidate.getGenericReturnType());
            if (raw != null && !java.lang.reflect.Modifier.isAbstract(raw.getModifiers())) {
                return candidate;
            }
        }
        return candidates.get(0);
    }

    private static boolean isMostSpecific(Method candidate, List<Method> candidates) {
        Class<?> raw = rawType(candidate.getGenericReturnType());
        if (raw == null) {
            return false;
        }
        for (Method other : candidates) {
            if (other == candidate) {
                continue;
            }
            Class<?> otherRaw = rawType(other.getGenericReturnType());
            if (otherRaw == null || !otherRaw.isAssignableFrom(raw)) {
                return false;
            }
        }
        return true;
    }

    /** The raw {@link Class} behind a generic return type, or {@code null} for unbound variables. */
    private static Class<?> rawType(Type type) {
        if (type instanceof Class<?> clazz) {
            return clazz;
        }
        if (type instanceof java.lang.reflect.ParameterizedType parameterized
                && parameterized.getRawType() instanceof Class<?> clazz) {
            return clazz;
        }
        if (type instanceof java.lang.reflect.WildcardType wildcard) {
            Type[] upper = wildcard.getUpperBounds();
            return upper.length > 0 ? rawType(upper[0]) : Object.class;
        }
        return null;
    }

    private boolean isLoadable(Class<?> type) {
        try {
            loader.loadClass(type.getName());
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    /**
     * A binding whose value type is the builder itself (a fluent factory that returns its own
     * builder class): the shape cannot be derived without a cycle, so it is reported as a gap and
     * falls back to an unconstrained shape (the key stays part of the closed schema).
     */
    private YamlProperty selfReferential(String key, String marker, String source, String description, boolean deprecated) {
        report.error(marker + " on " + source + " resolves to its own builder type; self-referential bindings "
                + "are not supported — declare the shape explicitly in the shape file");
        return property(key, new AnyShape(source, description, deprecated), description, deprecated, source);
    }

    private static YamlProperty property(String key, YamlShape shape, String description, boolean deprecated, String source) {
        return YamlProperty.builder()
                .key(key)
                .shape(shape)
                .description(description)
                .deprecated(deprecated)
                .source(source)
                .build();
    }
}
