/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.devtools.yaml.shape;

import java.util.Map;
import java.util.TreeMap;

/**
 * Structural identity of a shape — the canonical form generators use to (a) deduplicate identical
 * sub-objects into a single generated class and (b) detect merge conflicts (two shapes that must
 * occupy the same key but differ structurally).
 *
 * <p>Identity is structural only: descriptions, sources, deprecated and default-value metadata do
 * not participate (a key with a nicer Javadoc is still the same shape).
 */
public final class ShapeIdentity {

    private ShapeIdentity() {
    }

    /** The canonical structural string of {@code shape}. */
    public static String of(YamlShape shape) {
        StringBuilder sb = new StringBuilder();
        write(shape, sb);
        return sb.toString();
    }

    private static void write(YamlShape shape, StringBuilder sb) {
        switch (shape) {
            case ObjectShape o -> {
                sb.append('o');
                Map<String, YamlProperty> sorted = new TreeMap<>(o.properties());
                for (Map.Entry<String, YamlProperty> e : sorted.entrySet()) {
                    sb.append('[').append(e.getKey());
                    if (e.getValue().required()) {
                        sb.append('!');
                    }
                    if (e.getValue().nullable()) {
                        sb.append('~');
                    }
                    write(e.getValue().shape(), sb);
                    sb.append(']');
                }
            }
            case DelegatedObjectShape d -> {
                sb.append('o');
                Map<String, YamlProperty> sorted = new TreeMap<>(d.declared().properties());
                for (Map.Entry<String, YamlProperty> e : sorted.entrySet()) {
                    sb.append('[').append(e.getKey());
                    if (e.getValue().required()) {
                        sb.append('!');
                    }
                    write(e.getValue().shape(), sb);
                    sb.append(']');
                }
                for (String delegate : d.delegateTypes()) {
                    sb.append('<').append(delegate).append('>');
                }
            }
            case ListShape l -> {
                sb.append('l');
                write(l.item(), sb);
            }
            case MapShape m -> {
                sb.append('m');
                if (!m.restrictedKeys().isEmpty()) {
                    Map<String, String> sorted = new TreeMap<>();
                    for (String k : m.restrictedKeys()) {
                        sorted.put(k, k);
                    }
                    sb.append('{').append(String.join(",", sorted.keySet())).append('}');
                }
                write(m.value(), sb);
                if (m.nullableValue()) {
                    sb.append('~');
                }
            }
            case UnionShape u -> {
                sb.append('u');
                for (YamlShape alt : u.alternatives()) {
                    write(alt, sb);
                }
            }
            case LeafShape l -> {
                sb.append('t').append(l.type().name().charAt(0));
                if (!l.enumValues().isEmpty()) {
                    Map<String, String> sorted = new TreeMap<>();
                    for (String v : l.enumValues()) {
                        sorted.put(v, v);
                    }
                    sb.append('{').append(String.join(",", sorted.keySet())).append('}');
                }
            }
            case ScriptShape s -> sb.append(s.expression() ? "sx" : "s");
            case PathShape p -> sb.append('p');
            case AnyShape a -> sb.append('a');
        }
    }
}
