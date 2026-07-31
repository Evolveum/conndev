/*
 * Copyright (c) 2026 Evolveum and contributors
 * 
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 * 
 */
package com.evolveum.polygon.conndev.concepts;

public interface Fluent<F extends Fluent<F>> {

    @SuppressWarnings("unchecked")
    default F self() {
        return (F) this;
    }

}
