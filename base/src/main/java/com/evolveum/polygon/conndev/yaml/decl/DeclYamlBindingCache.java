/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.yaml.decl;

import com.evolveum.polygon.conndev.annotations.Script;
import com.evolveum.polygon.conndev.concepts.DefinitionValue;
import com.evolveum.polygon.conndev.annotations.Yaml;
import groovy.lang.Closure;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/**
 * Caches the YAML {@link DeclYamlBinding}s for a builder type. The reflection scan — walking the class and
 * its full interface hierarchy for the {@code @Yaml.*}-annotated methods — runs exactly once per
 * concrete class (memoised in a static map), so the first document of a type pays the scan and every
 * subsequent document (and every document of the same type) is a cache hit.
 */

// TODO: Consider merging with Binding
final class DeclYamlBindingCache {

    private static final Map<Class<?>, Map<String, DeclYamlBinding>> CACHE = new ConcurrentHashMap<>();

    private DeclYamlBindingCache() {
    }

    static Map<String, DeclYamlBinding> bindingsFor(Object target) {
        return CACHE.computeIfAbsent(target.getClass(), DeclYamlBindingCache::scan);
    }

    private static Map<String, DeclYamlBinding> scan(Class<?> clazz) {
        Map<String, DeclYamlBinding> map = new LinkedHashMap<>();
        for (Method method : bindingMethods(clazz)) {
            DeclYamlBinding binding = buildBinding(clazz, method);
            if (binding != null) {
                map.put(binding.key(), binding);
            }
        }
        return map;
    }

    /**
     * Collects the binding methods: the {@code @Yaml.*}-annotated ones, plus the
     * {@code @Script.*}-marked single-argument {@code Closure} methods whose binding key is not
     * already occupied by a {@code @Yaml.*} method (a Groovy convenience like {@code connId(Closure)}
     * must not shadow a {@code @Yaml.Sub connId()} accessor under the same key).
     */
    private static List<Method> bindingMethods(Class<?> clazz) {
        Map<String, Method> yamlBySignature = new LinkedHashMap<>();
        collectWhere(clazz, DeclYamlBindingCache::hasYamlMarker, yamlBySignature);

        Set<String> yamlKeys = new HashSet<>();
        for (Method method : yamlBySignature.values()) {
            yamlKeys.add(bindingKey(method));
        }

        Map<String, Method> bySignature = new LinkedHashMap<>(yamlBySignature);
        collectWhere(clazz, method -> isScriptedClosure(method) && !yamlKeys.contains(method.getName()), bySignature);
        return new ArrayList<>(bySignature.values());
    }

    private static boolean hasYamlMarker(Method method) {
        return method.getAnnotation(Yaml.Key.class) != null
                || method.getAnnotation(Yaml.Sub.class) != null
                || method.getAnnotation(Yaml.Custom.class) != null
                || method.getAnnotation(Yaml.Map.class) != null;
    }

    private static boolean isScriptedClosure(Method method) {
        return findScriptedClosureParam(method) != null;
    }

    /** The YAML key a {@code @Yaml.*} method binds: the {@code @Yaml.Key} value, else the method name. */
    private static String bindingKey(Method method) {
        Yaml.Key key = method.getAnnotation(Yaml.Key.class);
        return (key != null && !key.value().isEmpty()) ? key.value() : method.getName();
    }

    /** Walks the class and its full hierarchy, collecting the methods matching {@code predicate}. */
    private static void collectWhere(Class<?> type, Predicate<Method> predicate, Map<String, Method> bySignature) {
        for (Method method : type.getDeclaredMethods()) {
            if (predicate.test(method)) {
                bySignature.putIfAbsent(signature(method), method);
            }
        }
        if (type.isInterface()) {
            for (Class<?> superInterface : type.getInterfaces()) {
                collectWhere(superInterface, predicate, bySignature);
            }
        } else {
            Class<?> superClass = type.getSuperclass();
            if (superClass != null && superClass != Object.class) {
                collectWhere(superClass, predicate, bySignature);
            }
            for (Class<?> interfaceType : type.getInterfaces()) {
                collectWhere(interfaceType, predicate, bySignature);
            }
        }
    }

    private static String signature(Method method) {
        return method.getName() + "(" + Arrays.toString(method.getParameterTypes()) + ")";
    }

    /**
     * A single-argument method is a block-scalar-to-closure binding when its {@code Closure}
     * parameter is annotated {@code @Script.Runtime}; otherwise {@code null}.
     * {@code @Script.Initialization} closures are Groovy-DSL conveniences (the imperative form of
     * what the declarative YAML expresses) and are therefore not YAML bindings. The marker (not a
     * {@code @Yaml.*} method annotation) selects the binding.
     */
    private static Parameter findScriptedClosureParam(Method method) {
        if (method.getParameterCount() != 1) {
            return null;
        }
        Parameter param = method.getParameters()[0];
        if (param.getType() != Closure.class) {
            return null;
        }
        return param.getAnnotation(Script.Runtime.class) != null ? param : null;
    }

    private static DeclYamlBinding buildBinding(Class<?> targetClass, Method method) {
        Yaml.Key key = method.getAnnotation(Yaml.Key.class);
        Yaml.Sub sub = method.getAnnotation(Yaml.Sub.class);
        Yaml.Custom custom = method.getAnnotation(Yaml.Custom.class);
        Yaml.Map map = method.getAnnotation(Yaml.Map.class);
        Yaml.ValueParser valueParser = method.getAnnotation(Yaml.ValueParser.class);
        Parameter closureParam = findScriptedClosureParam(method);

        String bindingKey = (key != null && !key.value().isEmpty()) ? key.value() : method.getName();
        DeclYamlValueParser coercer = valueParser != null ? instantiate(valueParser.value()) : DeclDefaultValueParser.INSTANCE;
        MethodHandle resolved = unreflect(resolve(targetClass, method));

        if (sub != null) {
            return new DeclYamlBinding.Subtree(bindingKey, resolved);
        }
        if (closureParam != null) {
            return new DeclYamlBinding.RuntimeScript(bindingKey, resolved);
        }
        if (custom != null) {
            return new DeclYamlBinding.Custom(bindingKey, resolved, instantiateHandler(custom.value()));
        }
        if (map != null) {
            return new DeclYamlBinding.Map(resolved, coercer,targetClass, map);
        }
        if (key != null) {
            Class<?>[] params = method.getParameterTypes();
            if (params.length != 1) {
                throw new IllegalStateException("@Yaml.Key method '" + method.getName()
                        + "' must take exactly one parameter, found " + params.length);
            }
            Method dvMethod = findDefinitionValueOverload(targetClass, method.getName(), params[0]);
            MethodHandle dvOverload = dvMethod != null ? unreflect(dvMethod) : null;
            return new DeclYamlBinding.Property(bindingKey, resolved, dvOverload, params, coercer);
        }
        return null;
    }

    /** The most-derived public method matching the annotated method's signature. */
    private static Method resolve(Class<?> targetClass, Method annotated) {
        Class<?>[] params = annotated.getParameterTypes();
        for (Method method : targetClass.getMethods()) {
            if (method.getName().equals(annotated.getName()) && Arrays.equals(method.getParameterTypes(), params)) {
                return accessible(method);
            }
        }
        return accessible(annotated);
    }

    /** Builder methods are invoked reflectively across packages, so open them (a no-op if public). */
    private static Method accessible(Method method) {
        method.setAccessible(true);
        return method;
    }

    /**
     * The single-argument {@code String} factory method named {@code factoryName} (e.g.
     * {@code attribute}) on the target class — invoked with each map key to create the sub-builder.
     */
    static Method findFactoryMethod(Class<?> targetClass, String factoryName) {
        for (Method method : targetClass.getMethods()) {
            if (method.getName().equals(factoryName) && method.getParameterCount() == 1
                    && method.getParameterTypes()[0] == String.class) {
                return accessible(method);
            }
        }
        throw new IllegalStateException("No factory method '" + factoryName + "(String)' found on "
                + targetClass.getSimpleName());
    }

    /**
     * Unreflects the (public) builder method into a cached {@link MethodHandle}, so the binder calls
     * it without per-call reflection. The builder methods are public, so the package lookup may
     * unreflect them.
     */
    static MethodHandle unreflect(Method method) {
        try {
            return MethodHandles.lookup().unreflect(method);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Cannot unreflect builder method " + method, e);
        }
    }

    /**
     * The same-name single-argument {@link DefinitionValue} overload of a leaf method, if one exists.
     * The binder prefers it so the value — together with its YAML location — is carried as a
     * {@code DefinitionValue} instead of a bare primitive.
     */
    private static Method findDefinitionValueOverload(Class<?> targetClass, String name, Class<?> plainParam) {
        if (plainParam == DefinitionValue.class) {
            return null;
        }
        for (Method method : targetClass.getMethods()) {
            if (method.getName().equals(name) && method.getParameterCount() == 1
                    && method.getParameterTypes()[0] == DefinitionValue.class) {
                return accessible(method);
            }
        }
        return null;
    }

    private static DeclYamlValueParser instantiate(Class<? extends DeclYamlValueParser> type) {
        try {
            return type.getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot instantiate Coercer " + type.getName(), e);
        }
    }

    private static CustomYamlHandler instantiateHandler(Class<? extends CustomYamlHandler> type) {
        try {
            return type.getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot instantiate CustomYamlHandler " + type.getName(), e);
        }
    }
}
