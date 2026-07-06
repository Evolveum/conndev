package com.evolveum.polygon.conndev.concepts;

public interface FluentBuilder<B extends FluentBuilder<B,P>, P> extends Builder<P> {

    B self();

    P build();
}
