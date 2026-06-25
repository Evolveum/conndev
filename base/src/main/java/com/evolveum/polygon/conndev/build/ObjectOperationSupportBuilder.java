/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.build;

import com.evolveum.polygon.conndev.annotations.Script;
import com.evolveum.polygon.conndev.concepts.GroovyClosures;
import groovy.lang.Closure;
import groovy.lang.DelegatesTo;

public interface ObjectOperationSupportBuilder {

    SearchOperationBuilder search();

    ListOperationBuilder list();

    ReadOperationBuilder read();

    CreateOperationBuilder create();

    UpdateOperationBuilder update();

    DeleteOperationBuilder delete();

    default SearchOperationBuilder search(@DelegatesTo(value = SearchOperationBuilder.class, strategy = Closure.DELEGATE_ONLY) @Script.Initialization Closure<?> closure) {
        return GroovyClosures.callAndReturnDelegate(closure, search());
    }

    default ListOperationBuilder list(@DelegatesTo(value = ListOperationBuilder.class, strategy = Closure.DELEGATE_ONLY) @Script.Initialization Closure<?> closure) {
        return GroovyClosures.callAndReturnDelegate(closure, list());
    }

    default ReadOperationBuilder read(@DelegatesTo(value = ReadOperationBuilder.class, strategy = Closure.DELEGATE_ONLY) @Script.Initialization Closure<?> closure) {
        return GroovyClosures.callAndReturnDelegate(closure, read());
    }

    default CreateOperationBuilder create(@DelegatesTo(value = CreateOperationBuilder.class, strategy = Closure.DELEGATE_ONLY) @Script.Initialization Closure<?> closure) {
        return GroovyClosures.callAndReturnDelegate(closure, create());
    }

    default UpdateOperationBuilder update(@DelegatesTo(value = UpdateOperationBuilder.class, strategy = Closure.DELEGATE_ONLY) @Script.Initialization Closure<?> closure) {
        return GroovyClosures.callAndReturnDelegate(closure, update());
    }

    default DeleteOperationBuilder delete(@DelegatesTo(value = DeleteOperationBuilder.class, strategy = Closure.DELEGATE_ONLY) @Script.Initialization Closure<?> closure) {
        return GroovyClosures.callAndReturnDelegate(closure, delete());
    }
}
