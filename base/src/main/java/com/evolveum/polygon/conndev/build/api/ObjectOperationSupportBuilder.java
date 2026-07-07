/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.build.api;

import com.evolveum.polygon.conndev.annotations.Script;
import com.evolveum.polygon.conndev.concepts.GroovyClosures;
import groovy.lang.Closure;
import groovy.lang.DelegatesTo;

/**
 * Provides operation builders for each ConnId operation type within an object class.
 *
 * <p>This interface serves as the routing layer between an object class definition and the
 * specific operation builders (search, list, read, create, update, delete). Each method
 * returns a builder for the corresponding operation, with an overloaded closure-based
 * variant for inline DSL configuration.</p>
 *
 * <p>The closure variants use {@link groovy.lang.DelegatesTo DelegatesTo} to delegate
 * to the appropriate operation builder type, enabling type-safe Groovy DSL usage.</p>
 *
 * @see SearchOperationBuilder
 * @see ListOperationBuilder
 * @see ReadOperationBuilder
 * @see CreateOperationBuilder
 * @see UpdateOperationBuilder
 * @see DeleteOperationBuilder
 */
public interface ObjectOperationSupportBuilder {

    /**
     * Returns the builder for the search operation.
     *
     * @return a search operation builder
     */
    SearchOperationBuilder search();

    /**
     * Returns the builder for the list operation.
     *
     * @return a list operation builder
     */
    ListOperationBuilder list();

    /**
     * Returns the builder for the read operation.
     *
     * @return a read operation builder
     */
    ReadOperationBuilder read();

    /**
     * Returns the builder for the create operation.
     *
     * @return a create operation builder
     */
    CreateOperationBuilder create();

    /**
     * Returns the builder for the update operation.
     *
     * @return an update operation builder
     */
    UpdateOperationBuilder update();

    /**
     * Returns the builder for the delete operation.
     *
     * @return a delete operation builder
     */
    DeleteOperationBuilder delete();

    /**
     * Configures the search operation via a closure and returns the search builder.
     *
     * @param closure a closure that configures the {@link SearchOperationBuilder} instance
     * @return the configured search operation builder
     */
    default SearchOperationBuilder search(@DelegatesTo(value = SearchOperationBuilder.class, strategy = Closure.DELEGATE_ONLY) @Script.Initialization Closure<?> closure) {
        return GroovyClosures.callAndReturnDelegate(closure, search());
    }

    default ListOperationBuilder list(@DelegatesTo(value = ListOperationBuilder.class, strategy = Closure.DELEGATE_ONLY) @Script.Initialization Closure<?> closure) {
        return GroovyClosures.callAndReturnDelegate(closure, list());
    }

    /**
     * Configures the read operation via a closure and returns the read builder.
     *
     * @param closure a closure that configures the {@link ReadOperationBuilder} instance
     * @return the configured read operation builder
     */
    default ReadOperationBuilder read(@DelegatesTo(value = ReadOperationBuilder.class, strategy = Closure.DELEGATE_ONLY) @Script.Initialization Closure<?> closure) {
        return GroovyClosures.callAndReturnDelegate(closure, read());
    }

    default CreateOperationBuilder create(@DelegatesTo(value = CreateOperationBuilder.class, strategy = Closure.DELEGATE_ONLY) @Script.Initialization Closure<?> closure) {
        return GroovyClosures.callAndReturnDelegate(closure, create());
    }

    /**
     * Configures the update operation via a closure and returns the update builder.
     *
     * @param closure a closure that configures the {@link UpdateOperationBuilder} instance
     * @return the configured update operation builder
     */
    default UpdateOperationBuilder update(@DelegatesTo(value = UpdateOperationBuilder.class, strategy = Closure.DELEGATE_ONLY) @Script.Initialization Closure<?> closure) {
        return GroovyClosures.callAndReturnDelegate(closure, update());
    }

    default DeleteOperationBuilder delete(@DelegatesTo(value = DeleteOperationBuilder.class, strategy = Closure.DELEGATE_ONLY) @Script.Initialization Closure<?> closure) {
        return GroovyClosures.callAndReturnDelegate(closure, delete());
    }

    /**
     * Configures the list operation via a closure and returns the list builder.
     *
     * @param closure a closure that configures the {@link ListOperationBuilder} instance
     * @return the configured list operation builder
     */
    /**
     * Configures the create operation via a closure and returns the create builder.
     *
     * @param closure a closure that configures the {@link CreateOperationBuilder} instance
     * @return the configured create operation builder
     */
    /**
     * Configures the delete operation via a closure and returns the delete builder.
     *
     * @param closure a closure that configures the {@link DeleteOperationBuilder} instance
     * @return the configured delete operation builder
     */
}
