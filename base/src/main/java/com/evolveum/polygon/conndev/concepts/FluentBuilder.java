package com.evolveum.polygon.conndev.concepts;

public interface FluentBuilder<B extends FluentBuilder<B,P>, P> extends Fluent<B>, Builder<P> {

    P build();
}
