/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.yaml;

/**
 * Resolution of connector script resources shared by definition loaders that dispatch between the
 * Groovy and YAML front-ends: a referenced Groovy script missing from the bundle falls back to the
 * YAML document of the same name ({@code .groovy} → {@code .yaml}/{@code .yml}). This lets a
 * connector manifest keep referencing the conventional Groovy file names while the artifacts are
 * delivered as YAML.
 */
public final class ScriptResources {

    private ScriptResources() {
    }

    public static boolean isYaml(String resource) {
        return resource.endsWith(".yaml") || resource.endsWith(".yml");
    }

    /** Returns the resource to load — the referenced one, or its YAML fallback; fails when neither exists. */
    public static String resolveWithYamlFallback(Class<?> anchor, String resource) {
        if (anchor.getResource(resource) != null) {
            return resource;
        }
        if (resource.endsWith(".groovy")) {
            var base = resource.substring(0, resource.length() - ".groovy".length());
            for (var suffix : new String[] { ".yaml", ".yml" }) {
                var fallback = base + suffix;
                if (anchor.getResource(fallback) != null) {
                    return fallback;
                }
            }
            throw new IllegalArgumentException(
                    "Script resource not found: " + resource + " (nor its YAML fallback " + base + ".yaml)");
        }
        throw new IllegalArgumentException("Script resource not found: " + resource);
    }
}
