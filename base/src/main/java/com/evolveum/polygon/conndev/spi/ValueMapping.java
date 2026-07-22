/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.spi;

import org.identityconnectors.common.security.GuardedByteArray;
import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.framework.common.objects.ConnectorObjectReference;
import org.identityconnectors.framework.common.objects.EmbeddedObject;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Represents a mapping between data types used in an ConnId
 * and their corresponding wire types used in the protocol representation.
 * This interface allows for conversions between these two types and provides
 * metadata about the supported data types.
 */
public interface ValueMapping<C,P> {

    Map<Class<?>,ValueMapping<?,?>> IDENTITY_MAPPINGS = connIdMap(
        new Identity<>(String.class),
        new Identity<>(Long.class),
        new Identity<>(Character.class),
        new Identity<>(Double.class),
        new Identity<>(Float.class),
        new Identity<>(Integer.class),
        new Identity<>(Boolean.class),
        new Identity<>(Byte.class),
        new Identity<>(byte[].class),
        new Identity<>(BigDecimal.class),
        new Identity<>(BigInteger.class),
        new Identity<>(GuardedByteArray.class),
        new Identity<>(GuardedString.class),
        new Identity<>(Map.class),
        new Identity<>(ZonedDateTime.class),
        new Identity<>(ConnectorObjectReference.class),
        new Identity<>(EmbeddedObject.class)
    );

    Class<? extends C> connIdType();
    Class<? extends P> primaryWireType();
    default Set<Class<? extends P>> supportedWireTypes() {
        return Set.of(primaryWireType());
    }

    // FIXME: Rename toProtocolValue
    P toWireValue(C value) throws  IllegalArgumentException;

    C toConnIdValue(P value) throws IllegalArgumentException;

    static <C> C checkValueInstanceOf(Class<C> type, Object value) {
        if (value == null) {
            return null;
        }
        if (type.isInstance(value)) {
            return type.cast(value);
        }
        throw new IllegalArgumentException("Value " + value + " is not of supported type " + type.getSimpleName());
    }

    static <C> ValueMapping<C,C> identity(Class<C> type) {
        var builtIn = IDENTITY_MAPPINGS.get(type);
        if (builtIn != null) {
            return (ValueMapping<C,C>) builtIn;
        }
        return new Identity<>(type);
    }

    static <C,P> ValueMapping<C,P> conversion(Class<C> connIdType, Class<P> protocolType, Function<P, C> toConnId, Function<C, P> toProtocol) {
        return new FromFunctions<>(connIdType, protocolType, toConnId, toProtocol);
    }

    static Map<Class<?>, ValueMapping<?,?>> connIdMap(ValueMapping<?,?>... mappings) {
        Map<Class<?>, ValueMapping<?,?>> connIdMap = new HashMap<>();
        for (ValueMapping<?,?> mapping : mappings) {
            connIdMap.put(mapping.connIdType(), mapping);
        }
        return Map.copyOf(connIdMap);
    }


    /**
     *
     */
    record Identity<C>(Class<C> connIdType) implements ValueMapping<C,C> {

        @java.lang.Override
        public Class<? extends C> primaryWireType() {
            return connIdType;
        }

        @java.lang.Override
        public C toConnIdValue(C value) throws IllegalArgumentException {
            return checkValueInstanceOf(connIdType, value);
        }

        @java.lang.Override
        public C toWireValue(C value) throws IllegalArgumentException {
            return checkValueInstanceOf(connIdType, value);
        }
    }

    record Chain<C,I,P>(ValueMapping<C,I> override, ValueMapping<I,P> protocol) implements ValueMapping<C,P> {

        @java.lang.Override
        public Class<? extends C> connIdType() {
            return override.connIdType();
        }

        @java.lang.Override
        public Class<? extends P> primaryWireType() {
            return protocol.primaryWireType();
        }

        @java.lang.Override
        public Set<Class<? extends P>> supportedWireTypes() {
            return protocol.supportedWireTypes();
        }

        @java.lang.Override
        public P toWireValue(C value) throws IllegalArgumentException {
            return protocol.toWireValue(override.toWireValue(value));
        }

        @java.lang.Override
        public C toConnIdValue(P value) throws IllegalArgumentException {
            return override.toConnIdValue(protocol.toConnIdValue(value));
        }
    }

    record FromFunctions<C,P>(Class<C> connIdType, Class<P> protocolType, Function<P,C> toConnId, Function<C,P> toProtocol) implements ValueMapping<C,P> {

        @java.lang.Override
        public Class<P> primaryWireType() {
            return protocolType;
        }

        @java.lang.Override
        public P toWireValue(C value) throws IllegalArgumentException {
            return value != null ? toProtocol.apply(checkValueInstanceOf(connIdType(), value)) : null;
        }

        @java.lang.Override
        public C toConnIdValue(P value) throws IllegalArgumentException {
            return value != null ? toConnId.apply(checkValueInstanceOf(primaryWireType(), value)) : null;
        }
    }
}
