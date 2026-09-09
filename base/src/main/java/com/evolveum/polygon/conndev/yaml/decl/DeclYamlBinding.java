/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.yaml.decl;

import com.evolveum.polygon.conndev.concepts.CheckedCallable;
import com.evolveum.polygon.conndev.concepts.CheckedRunnable;
import com.evolveum.polygon.conndev.concepts.DefinitionValue;
import com.evolveum.polygon.conndev.concepts.SourceLocation;
import com.evolveum.polygon.conndev.annotations.Yaml;
import groovy.lang.Closure;

import java.lang.invoke.MethodHandle;

/**
 * A single resolved YAML binding for a builder method: the YAML {@code key}, the cached
 * {@link MethodHandle} to invoke, and — depending on the {@link Kind} — the {@code DefinitionValue}
 * overload to prefer (leaf), the sub-builder accessor (sub), the structural handler (custom) or the
 * map factory method handle (map). The handles are unreflected once per binding (not per call).
 */
abstract sealed class DeclYamlBinding {

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

    public static final class Subtree extends DeclYamlBinding {
        public Subtree(String bindingKey, MethodHandle resolved) {
            super(bindingKey, resolved);
        }

        @Override
        void apply(DeclYamlBinder binder, Object target, LocatedNode value, SourceLocation location) {
            Object subBuilder = wrapThrowable( () -> method.bindTo(target).invoke());
            binder.bind(value, subBuilder);
        }
    }

    public static final class RuntimeScript extends DeclYamlBinding {
        public RuntimeScript(String bindingKey, MethodHandle resolved) {
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

    public static final class Custom extends DeclYamlBinding {

        private final CustomYamlHandler custom;

        public Custom(String bindingKey, MethodHandle resolved, CustomYamlHandler customYamlHandler) {
            super(bindingKey, resolved);
            this.custom = customYamlHandler;
        }

        @Override
        void apply(DeclYamlBinder binder, Object target, LocatedNode value, SourceLocation location) {
            custom.apply(binder, target, value);
        }
    }

    public static final class Map extends DeclYamlBinding {

        public Map(MethodHandle resolved, DeclYamlValueParser coercer, Class<?> targetClass, Yaml.Map map) {
            super(map.value(), resolved);
        }

        /**
         * Binds a map of sub-builders: each entry's key is passed to the factory method to create the
         * sub-builder, and the entry's value sub-map is bound onto it.
         */
        @Override
        void apply(DeclYamlBinder binder, Object target, LocatedNode value, SourceLocation location) {
            for (LocatedNode.Entry entry : StructuralSupport.mapEntries(value, key())) {
                Object subBuilder = invoke(method, target, entry.key());
                binder.bind(entry.value(), subBuilder);
            }
        }
    }

    public static final class Property extends DeclYamlBinding {

        private final MethodHandle dvMethod;
        private final Class<?> paramType;
        private final DeclYamlValueParser coercer;

        public Property(String bindingKey, MethodHandle resolved, MethodHandle dvOverload, Class<?>[] params, DeclYamlValueParser coercer) {
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
                    wrapThrowable( () ->dvMethod.bindTo(target).invokeWithArguments(DefinitionValue.from(coerced, location)));
            } else {
                invoke(method, target, coerced);
            }
        }
    }

    private static Object invoke(MethodHandle handle, Object target, Object... args) {
        try {
            return handle.bindTo(target).invokeWithArguments(args);
        } catch (RuntimeException | Error e) {
            throw e; // the builder's own failure, already unwrapped by the handle
        } catch (Throwable t) {
            throw new IllegalArgumentException("Error invoking builder method: " + t.getMessage(), t);
        }
    }

    private static void wrapThrowable(CheckedRunnable<Throwable> runnable) {
        try {
            runnable.run();
        } catch (RuntimeException | Error e) {
            throw e; // the builder's own failure, already unwrapped by the handle
        } catch (Throwable t) {
            throw new IllegalArgumentException("Error invoking builder method: " + t.getMessage(), t);
        }
    }

    private static <R> R wrapThrowable(CheckedCallable<R,Throwable> runnable) {
        try {
            return runnable.call();
        } catch (RuntimeException | Error e) {
            throw e; // the builder's own failure, already unwrapped by the handle
        } catch (Throwable t) {
            throw new IllegalArgumentException("Error invoking builder method: " + t.getMessage(), t);
        }
    }
}
