/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.concepts;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * A path consisting of ordered components, resolved step by step against a value.
 *
 * Resolution walks the components from left to right: the first component is resolved
 * against the root value, and each following component is resolved against the value
 * produced by the previous step.
 *
 * @param <C> the type of the path components
 */
public interface Path<C> {

    /**
     * Returns the ordered components of this path.
     */
    Collection<C> components();

    /**
     * Returns {@code true} if this path has no components.
     *
     * An empty path resolves to the root value itself, without invoking the resolver.
     */
    default boolean isEmpty() {
        return components().isEmpty();
    }

    /**
     * Resolves the path and returns the value obtained after the last component.
     *
     * The root value is used as the starting value, and each path component is resolved
     * using the value produced by the previous resolution step. An empty path resolves
     * to the root value itself.
     *
     * @param root the starting value to resolve against
     * @param resolver the resolver that processes each component
     * @return the value resolved after the last component, or the root value if the path is empty
     * @throws E if the resolver throws an exception while resolving a component
     */
    default <O, E extends Exception> O resolve(O root, CheckedResolver<O, C, E> resolver) throws E {
        if (isEmpty()) {
            return root;
        }
        return resolveAll(root, resolver).getLast().value();
    }

    /**
     * Resolves all components of this path by applying the supplied resolver sequentially.
     *
     * The root value is used as the starting value, and each path component is resolved
     * using the value produced by the previous resolution step.
     *
     * @param root the starting value to resolve against
     * @param resolver the resolver that processes each component
     * @return one {@link ResolvedPair} per path component, in path order
     * @throws E if the resolver throws an exception while resolving a component
     */
    default <O, E extends Exception> List<ResolvedPair<C, O>> resolveAll(O root, CheckedResolver<O, C, E> resolver) throws E {
        var walked = new ArrayList<ResolvedPair<C, O>>();
        O resolved = root;
        for (C component : components()) {
            resolved = resolver.resolve(List.copyOf(walked), resolved, component);
            walked.add(new ResolvedPair<>(component, resolved));
        }
        return List.copyOf(walked);
    }

    /**
     * A single step of path resolution: a component and the value obtained by resolving it.
     *
     * @param component the path component that was resolved
     * @param value the value obtained by resolving the component
     */
    record ResolvedPair<C, O>(C component, O value) {
    }

    /**
     * Resolves the components of a path against a value, one component at a time.
     *
     * The resolver is invoked once per component, walking the path from left to right. It
     * receives the value obtained so far together with the next component, and returns the
     * value for the following step. A resolver must be prepared to receive a {@code null}
     * value, which means that the path can not be resolved any further.
     *
     * @param <O> the type of the values being resolved
     * @param <C> the type of the path components
     * @param <E> the type of exception the resolver may throw
     */
    @FunctionalInterface
    interface CheckedResolver<O, C, E extends Exception> {

        /**
         * Resolves the next component of the path.
         *
         * @param resolvedPath the component and value pairs of the steps already processed
         * @param resolvedValue the value obtained from the previously resolved component, usually the parent of the next one
         * @param next the next component to resolve
         * @return the value obtained by resolving the component
         * @throws E if the component can not be resolved
         */
        O resolve(List<ResolvedPair<C, O>> resolvedPath, O resolvedValue, C next) throws E;
    }

    /**
     * A {@link CheckedResolver} that does not declare checked exceptions.
     *
     * @param <O> the type of the values being resolved
     * @param <C> the type of the path components
     */
    @FunctionalInterface
    interface Resolver<O, C> extends CheckedResolver<O, C, RuntimeException> {
    }

}
