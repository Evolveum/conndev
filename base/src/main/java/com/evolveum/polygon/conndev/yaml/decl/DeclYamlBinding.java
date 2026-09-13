/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.yaml.decl;

import com.evolveum.polygon.conndev.annotations.Script;
import com.evolveum.polygon.conndev.annotations.Yaml;
import com.evolveum.polygon.conndev.api.AttributePathDeclaration;
import com.evolveum.polygon.conndev.api.AttributePathFormat;
import com.evolveum.polygon.conndev.concepts.CheckedCallable;
import com.evolveum.polygon.conndev.concepts.CheckedRunnable;
import com.evolveum.polygon.conndev.concepts.DefinitionValue;
import com.evolveum.polygon.conndev.concepts.SourceLocation;
import groovy.lang.Closure;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/**
 * A single resolved YAML binding for a builder method: the YAML {@code key}, the cached
 * {@link MethodHandle} to invoke, and — depending on the kind — the {@code DefinitionValue}
 * overload to prefer (leaf), the sub-builder accessor (sub), the structural handler (custom) or the
 * map factory method handle (map). The handles are unreflected once per binding (not per call).
 *
 * <p>The bindings of a builder type are also cached here: the reflection scan — walking the class and
 * its full interface hierarchy for the {@code @Yaml.*}-annotated methods — runs exactly once per
 * concrete class (memoised in a static map), so the first document of a type pays the scan and every
 * subsequent document (and every document of the same type) is a cache hit.
 */
abstract sealed class DeclYamlBinding {

    private static final Map<Class<?>, Map<String, DeclYamlBinding>> CACHE = new ConcurrentHashMap<>();

    /** The bindings of the concrete class of {@code target}, scanned and cached once per class. */
    static Map<String, DeclYamlBinding> bindingsFor(Object target) {
        return CACHE.computeIfAbsent(target.getClass(), DeclYamlBinding::scan);
    }

    abstract void apply(DeclYamlBinder binder, Object target, LocatedNode value, SourceLocation location);

    protected final String key;
    protected final MethodHandle method;

    DeclYamlBinding(String key, MethodHandle method) {
        this.key = key;
        this.method = method;
    }

    String key() {
        return key;
    }

    static final class Subtree extends DeclYamlBinding {
        Subtree(String bindingKey, MethodHandle resolved) {
            super(bindingKey, resolved);
        }

        @Override
        void apply(DeclYamlBinder binder, Object target, LocatedNode value, SourceLocation location) {
            Object subBuilder = wrapThrowable(() -> method.bindTo(target).invoke());
            binder.bind(value, subBuilder);
        }
    }

    static final class RuntimeScript extends DeclYamlBinding {
        RuntimeScript(String bindingKey, MethodHandle resolved) {
            super(bindingKey, resolved);
        }

        @Override
        void apply(DeclYamlBinder binder, Object target, LocatedNode value, SourceLocation location) {
            if (value.isNull()) {
                return;
            }
            String text = value.text();
            if (text == null) {
                throw new IllegalArgumentException("Expected a block scalar (Groovy source) for '"
                        + key() + "' at " + location);
            }
            // TODO: Maybe there is way to base source locations for groovy script
            Closure<?> closure = binder.compileClosure(text);
            invoke(method, target, closure);
        }
    }

    static final class Custom extends DeclYamlBinding {

        private final CustomYamlHandler custom;

        Custom(String bindingKey, MethodHandle resolved, CustomYamlHandler customYamlHandler) {
            super(bindingKey, resolved);
            this.custom = customYamlHandler;
        }

        @Override
        void apply(DeclYamlBinder binder, Object target, LocatedNode value, SourceLocation location) {
            custom.apply(binder, target, value);
        }
    }

    /**
     * Binds a map of sub-builders: each entry's key is passed to the factory method to create the
     * sub-builder, and the entry's value sub-map is bound onto it.
     */
    static final class MapBinding extends DeclYamlBinding {

        MapBinding(String bindingKey, MethodHandle factory) {
            super(bindingKey, factory);
        }

        @Override
        void apply(DeclYamlBinder binder, Object target, LocatedNode value, SourceLocation location) {
            for (LocatedNode.Entry entry : StructuralSupport.mapEntries(value, key())) {
                Object subBuilder = invoke(method, target, entry.key());
                binder.bind(entry.value(), subBuilder);
            }
        }
    }

    static final class Property extends DeclYamlBinding {

        private final MethodHandle dvMethod;
        private final Class<?> paramType;
        private final DeclYamlValueParser coercer;

        Property(String bindingKey, MethodHandle resolved, MethodHandle dvOverload, Class<?>[] params, DeclYamlValueParser coercer) {
            super(bindingKey, resolved);
            dvMethod = dvOverload;
            this.paramType = params[0];
            this.coercer = coercer;
        }

        @Override
        void apply(DeclYamlBinder binder, Object target, LocatedNode value, SourceLocation location) {
            if (value.isNull()) {
                return; // an explicit/empty null leaves the builder default untouched
            }
            Object coerced = coercer.coerce(value, location, paramType);
            if (dvMethod != null) {
                wrapThrowable(() -> dvMethod.bindTo(target).invokeWithArguments(DefinitionValue.from(coerced, location)));
            } else {
                invoke(method, target, coerced);
            }
        }
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
        collectWhere(clazz, DeclYamlBinding::hasYamlMarker, yamlBySignature);

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
                || method.getAnnotation(Yaml.Map.class) != null
                || method.getAnnotation(Yaml.Path.class) != null;
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
        Yaml.Path path = method.getAnnotation(Yaml.Path.class);
        Parameter closureParam = findScriptedClosureParam(method);

        String bindingKey = (key != null && !key.value().isEmpty()) ? key.value() : method.getName();
        MethodHandle resolved = unreflect(resolve(targetClass, method));

        if (sub != null) {
            return new Subtree(bindingKey, resolved);
        }
        if (closureParam != null) {
            return new RuntimeScript(bindingKey, resolved);
        }
        if (custom != null) {
            return new Custom(bindingKey, resolved, instantiateHandler(custom.value()));
        }
        if (map != null) {
            return new MapBinding(map.value(), resolved);
        }
        if (key != null || path != null) {
            Class<?>[] params = method.getParameterTypes();
            if (params.length != 1) {
                throw new IllegalStateException((path != null ? "@Yaml.Path" : "@Yaml.Key") + " method '"
                        + method.getName() + "' must take exactly one parameter, found " + params.length);
            }
            DeclYamlValueParser coercer;
            if (path != null) {
                if (params[0] != AttributePathDeclaration.class) {
                    throw new IllegalStateException("@Yaml.Path method '" + method.getName()
                            + "' must take a single AttributePathDeclaration parameter, found " + params[0].getName());
                }
                coercer = new DeclPathValueParser(resolvePathFormat(path.value()));
            } else {
                coercer = valueParser != null ? instantiate(valueParser.value()) : DeclDefaultValueParser.INSTANCE;
            }
            Method dvMethod = findDefinitionValueOverload(targetClass, method.getName(), params[0]);
            MethodHandle dvOverload = dvMethod != null ? unreflect(dvMethod) : null;
            return new Property(bindingKey, resolved, dvOverload, params, coercer);
        }
        return null;
    }

    /**
     * Resolves the format instance of a {@code @Yaml.Path} binding: the format class must expose the
     * {@code public static final INSTANCE} singleton declared by the framework convention (all
     * built-in formats do).
     */
    private static AttributePathFormat<String> resolvePathFormat(Class<? extends AttributePathFormat<String>> type) {
        try {
            var field = type.getField("INSTANCE");
            if (Modifier.isStatic(field.getModifiers()) && field.getType() == type) {
                return (AttributePathFormat<String>) field.get(null);
            }
        } catch (ReflectiveOperationException ignored) {
            // fall through to the failure below
        }
        throw new IllegalStateException("Path format " + type.getName()
                + " must expose a 'public static final INSTANCE' field");
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
     * Unreflects the (public) builder method into a cached {@link MethodHandle}, so the binder calls
     * it without per-call reflection. The builder methods are public, so the package lookup may
     * unreflect them.
     */
    private static MethodHandle unreflect(Method method) {
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

    private static Object invoke(MethodHandle handle, Object target, Object... args) {
        return wrapThrowable(() -> handle.bindTo(target).invokeWithArguments(args));
    }

    private static void wrapThrowable(CheckedRunnable<Throwable> runnable) {
        wrapThrowable(runnable::run);
    }

    private static <R> R wrapThrowable(CheckedCallable<R, Throwable> callable) {
        try {
            return callable.call();
        } catch (RuntimeException | Error e) {
            throw e; // the builder's own failure, already unwrapped by the handle
        } catch (Throwable t) {
            throw new IllegalArgumentException("Error invoking builder method: " + t.getMessage(), t);
        }
    }
}
