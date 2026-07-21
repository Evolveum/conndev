/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.schema;

import com.evolveum.polygon.conndev.spi.ValueMapping;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.Objects;
import java.util.function.Function;

/**
 * A {@link ValueMapping} wrapper that bridges type mismatches between ConnId and protocol types.
 * <p>
 * When a schema declares a value mapping with one ConnId type but the connector expects
 * or produces a different type (e.g., overriding a mapping to use {@code Object} or
 * {@code String} instead of the original ConnId type), this wrapper adapts the conversion
 * by delegating to an underlying mapping with an intermediate conversion step.
 * <p>
 * The conversion pipeline works as follows:
 * <ul>
 *   <li><b>{@code toWireValue}:</b> Convert from override type ({@code O}) to delegate type ({@code D}),
 *       then delegate to the inner mapping's {@code toWireValue} to produce the protocol value ({@code P}).</li>
 *   <li><b>{@code toConnIdValue}:</b> Delegate to the inner mapping's {@code toConnIdValue} to get the
 *       delegate type value ({@code D}), then convert from delegate to override type ({@code O}).</li>
 * </ul>
 *
 * @param <O> the override ConnId type (the type exposed by this mapping)
 * @param <D> the delegate ConnId type (the type expected by the underlying mapping)
 * @param <P> the protocol (wire) value type
 */
public record ValueTypeOverrideMapping<O, D, P>(Class<O> connIdType, ValueMapping<D,P> impl, Function<O,D> toDelegate, Function<D, O> toOverride)  implements ValueMapping<O,P> {

    /**
     * Factory method that wraps or casts a value mapping to expose {@code Object} as the ConnId type.
     * <p>
     * If the given mapping already uses {@code Object} as its ConnId type, a plain cast is returned.
     * If the given mapping uses a known type (Integer, Long, Number) and the override type is
     * {@code String}, a conversion is inserted to parse or serialize between String and the original type.
     *
     * @param <P> the protocol (wire) value type
     * @param connIdType the desired override ConnId type (typically {@code Object} or {@code String})
     * @param valueMapping the underlying value mapping to wrap
     * @return a value mapping with the overridden ConnId type, or the original if already compatible
     * @throws IllegalArgumentException if the type combination is not supported
     */
    public static <P> ValueMapping<Object, P> of(Class<?> connIdType, ValueMapping<?, P> valueMapping) {
        if (Objects.equals(connIdType, valueMapping.connIdType())) {
            return cast(Object.class, valueMapping);
        }
        if (String.class.equals(connIdType)) {
            return cast(Object.class, stringType(valueMapping));
        }
        throw new IllegalArgumentException("Unsupported override type combination: " + connIdType + " and " + valueMapping.toString());
    }

    /**
     * Creates a String-typed override mapping by inserting conversions between the original
     * ConnId type (Integer, Long, or Number) and String.
     *
     * @param <P> the protocol (wire) value type
     * @param valueMapping the underlying value mapping with a numeric ConnId type
     * @return a value mapping that uses {@code String} as the ConnId type with appropriate conversions
     * @throws IllegalArgumentException if the underlying mapping uses an unsupported ConnId type
     */
    private static <P> ValueMapping<String,P> stringType(ValueMapping<?,P> valueMapping) {
        var original = valueMapping.connIdType();
        if (Integer.class.equals(original)) {
            return new ValueTypeOverrideMapping<>(String.class, cast(Integer.class,valueMapping),
                Integer::parseInt, Object::toString
            );
        }
        if (Long.class.equals(original)) {
            return new ValueTypeOverrideMapping<>(String.class, cast(Long.class, valueMapping),
                    Long::parseLong, Object::toString);
        }
        if (BigDecimal.class.equals(original)) {
            return new ValueTypeOverrideMapping<>(String.class, cast(BigDecimal.class, valueMapping),
                    BigDecimal::new, Object::toString);
        }
        if (Number.class.equals(original)) {
            return new ValueTypeOverrideMapping<>(String.class, cast(Number.class, valueMapping),
                    Long::parseLong, Object::toString);
        }
        if (ZonedDateTime.class.equals(original)) {
            return new ValueTypeOverrideMapping<>(String.class, cast(ZonedDateTime.class, valueMapping),
                    ZonedDateTime::parse,Object::toString);
        }
        throw new IllegalArgumentException("Unsupported override type combination: " + valueMapping.connIdType());
    }

    /**
     * Converts a value mapping's ConnId type using unchecked cast, with planned support
     * for additional ConnId conversion types such as Boolean, Byte, Short, Float, and Double.
     * <p>
     * Currently this method wraps the given mapping and changes its ConnId type to the
     * specified type. Future enhancements may insert actual conversion functions.
     *
     * @param <O> the override ConnId type
     * @param <P> the protocol (wire) value type
     * @param type the desired ConnId type
     * @param valueMapping the underlying value mapping to cast
     * @return the underlying mapping cast to the new ConnId type
     */
    @SuppressWarnings("unchecked")
    private static <O, P> ValueMapping<O,P> cast(Class<O> type, ValueMapping<?,P> valueMapping) {
        return (ValueMapping<O, P>) valueMapping;
    }

    /**
     * Returns the ConnId value type for this mapping.
     *
     * @return the override ConnId type class
     */
    @Override
    public Class<O> connIdType() {
        return connIdType;
    }

    /**
     * Returns the protocol (wire) value type by delegating to the underlying mapping.
     *
     * @return the protocol type class
     */
    @Override
    public Class<? extends P> primaryWireType() {
        return impl.primaryWireType();
    }

    /**
     * Converts a value from the override ConnId type to the protocol (wire) type.
     * <p>
     * First converts the override value ({@code O}) to the delegate type ({@code D})
     * using {@link #toDelegate()}, then delegates to the underlying mapping's
     * {@code toWireValue()} to produce the protocol value ({@code P}).
     *
     * @param value the override ConnId-side value
     * @return the converted protocol-side value
     * @throws IllegalArgumentException if the underlying conversion fails
     */
    @Override
    public P toWireValue(O value) throws IllegalArgumentException {
        return impl.toWireValue(toDelegate.apply(value));
    }

    /**
     * Converts a value from the protocol (wire) type to the override ConnId type.
     * <p>
     * First delegates to the underlying mapping's {@code toConnIdValue()} to obtain
     * the delegate type value ({@code D}), then converts it to the override type ({@code O})
     * using {@link #toOverride()}.
     *
     * @param value the protocol-side value
     * @return the converted override ConnId-side value
     * @throws IllegalArgumentException if the underlying conversion fails
     */
    @Override
    public O toConnIdValue(P value) throws IllegalArgumentException {
        return toOverride.apply(impl.toConnIdValue(value));
    }

}