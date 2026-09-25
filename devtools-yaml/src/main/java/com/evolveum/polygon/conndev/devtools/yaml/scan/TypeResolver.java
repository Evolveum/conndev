/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.devtools.yaml.scan;

import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.HashMap;
import java.util.Map;

/**
 * Resolves the generic type arguments of a resolved method against the concrete class it was
 * looked up on — the builder CRTP generics ({@code <B, A, R, P>}) collapse to the concrete
 * attribute/reference/sub-builder types only after this substitution.
 *
 * <p>Only the resolution depth the builders need is supported: type variables bound through the
 * concrete class's generic superclass / interface chain, and parameterization of those bindings.
 * Anything still unbound is reported by the caller as a gap.
 */
final class TypeResolver {

    private final Class<?> context;

    private TypeResolver(Class<?> context) {
        this.context = context;
    }

    /** Resolves {@code type} in the context of {@code contextClass}; returns the raw {@link Class} when possible. */
    static Class<?> resolveRaw(Type type, Class<?> contextClass) {
        Type resolved = new TypeResolver(contextClass).resolve(type);
        if (resolved instanceof Class<?> clazz) {
            return clazz;
        }
        if (resolved instanceof ParameterizedType parameterized) {
            Type raw = parameterized.getRawType();
            if (raw instanceof Class<?> clazz) {
                return clazz;
            }
        }
        return null;
    }

    private Type resolve(Type type) {
        if (type instanceof Class<?> clazz) {
            return clazz;
        }
        if (type instanceof TypeVariable<?> variable) {
            return bindingFor(variable);
        }
        if (type instanceof ParameterizedType parameterized) {
            Type raw = resolve(parameterized.getRawType());
            Type[] args = parameterized.getActualTypeArguments();
            if (raw instanceof Class<?> rawClass && java.util.Arrays.stream(args).noneMatch(TypeVariable.class::isInstance)) {
                return rawClass; // arguments only matter for deeper bindings; the raw type is what callers need
            }
            Type[] resolvedArgs = new Type[args.length];
            for (int i = 0; i < args.length; i++) {
                resolvedArgs[i] = resolve(args[i]);
            }
            if (raw instanceof Class<?> rawClass) {
                return new ParameterizedImpl(rawClass, resolvedArgs);
            }
            return raw;
        }
        if (type instanceof WildcardType wildcard) {
            Type[] upper = wildcard.getUpperBounds();
            return upper.length > 0 ? resolve(upper[0]) : Object.class;
        }
        if (type instanceof GenericArrayType array) {
            return resolve(array.getGenericComponentType());
        }
        return Object.class;
    }

    private Type bindingFor(TypeVariable<?> variable) {
        for (Map.Entry<String, Type> binding : bindings(variable.getName()).entrySet()) {
            if (binding.getKey().equals(variable.getName())) {
                return resolve(binding.getValue());
            }
        }
        return variable; // unbound — surfaced by the caller
    }

    /**
     * The type-variable bindings visible in {@code context}: for every generic supertype
     * (class or interface) of the context, its type variables bound to the supplied arguments.
     */
    private Map<String, Type> bindings(String name) {
        Map<String, Type> result = new HashMap<>();
        collectBindings(context, result);
        return result;
    }

    private void collectBindings(Class<?> type, Map<String, Type> result) {
        if (type == null || type == Object.class) {
            return;
        }
        if (type.isInterface()) {
            for (Type generic : type.getGenericInterfaces()) {
                bind(generic, result);
            }
            for (Class<?> parent : type.getInterfaces()) {
                collectBindings(parent, result);
            }
        } else {
            Type genericSuper = type.getGenericSuperclass();
            bind(genericSuper, result);
            collectBindings(type.getSuperclass(), result);
            for (Class<?> parent : type.getInterfaces()) {
                collectBindings(parent, result);
            }
        }
    }

    private void bind(Type generic, Map<String, Type> result) {
        if (generic instanceof ParameterizedType parameterized) {
            Type raw = parameterized.getRawType();
            if (!(raw instanceof Class<?> rawClass)) {
                return;
            }
            TypeVariable<?>[] variables = rawClass.getTypeParameters();
            Type[] arguments = parameterized.getActualTypeArguments();
            for (int i = 0; i < variables.length && i < arguments.length; i++) {
                result.putIfAbsent(variables[i].getName(), arguments[i]);
            }
        } else if (generic instanceof Class<?> clazz) {
            collectBindings(clazz, result);
        }
    }

    private static final class ParameterizedImpl implements ParameterizedType {

        private final Type rawType;
        private final Type[] actualTypeArguments;

        ParameterizedImpl(Type rawType, Type[] actualTypeArguments) {
            this.rawType = rawType;
            this.actualTypeArguments = actualTypeArguments;
        }

        @Override
        public Type[] getActualTypeArguments() {
            return actualTypeArguments;
        }

        @Override
        public Type getRawType() {
            return rawType;
        }

        @Override
        public Type getOwnerType() {
            return null;
        }

        @Override
        public int hashCode() {
            return java.util.Arrays.hashCode(actualTypeArguments) * 31 + rawType.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof ParameterizedType other)) {
                return false;
            }
            return rawType.equals(other.getRawType())
                    && java.util.Arrays.equals(actualTypeArguments, other.getActualTypeArguments());
        }
    }
}
