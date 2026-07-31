/*
 * Copyright (c) 2026 Evolveum and contributors
 * 
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 * 
 */
package com.evolveum.polygon.conndev.concepts;

public interface FluentBuilder<B extends FluentBuilder<B,P>, P> extends Fluent<B>, Builder<P> {

    P build();
}
