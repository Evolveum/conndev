package com.evolveum.polygon.conndev.concepts;

public interface Fluent<F extends Fluent<F>> {

    @SuppressWarnings("unchecked")
    default F self() {
        return (F) this;
    }

}
