/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.devtools.yaml.scan;

import com.evolveum.polygon.conndev.devtools.yaml.config.ShapeFileConfig.HandlerConfig;
import com.evolveum.polygon.conndev.devtools.yaml.shape.YamlShape;

import java.util.List;
import java.util.Optional;

/**
 * Resolves the shape of a {@code @Yaml.Custom} handler for the type being scanned. A shape-file
 * declaration matches when its {@code handler} name matches and — when the declaration carries
 * {@code on} types — one of them is a supertype (or the same type) of the target; a declaration
 * without {@code on} applies everywhere. No match is a gap (the shape file must stay in lockstep
 * with the handlers).
 */
public final class HandlerShapes {

    private final List<HandlerConfig> configs;
    private final ClassLoader loader;
    private final GapReport report;

    public HandlerShapes(List<HandlerConfig> configs, ClassLoader loader, GapReport report) {
        this.configs = List.copyOf(configs);
        this.loader = loader;
        this.report = report;
    }

    /** The declared shape for {@code handlerFqn} in the context of {@code target}, or empty (a gap). */
    public Optional<YamlShape> resolve(String handlerFqn, Class<?> target) {
        List<HandlerConfig> matches = configs.stream()
                .filter(c -> c.handler().equals(handlerFqn))
                .filter(c -> c.onEmpty() || c.on().stream().anyMatch(fqn -> assignable(target, fqn)))
                .toList();
        if (matches.isEmpty()) {
            report.error("No shape declared for @Yaml.Custom handler " + handlerFqn
                    + " (target " + target.getName() + "); add it to the shape file's customHandlers");
            return Optional.empty();
        }
        if (matches.size() > 1) {
            HandlerConfig first = matches.getFirst();
            report.error("Ambiguous @Yaml.Custom handler declarations for " + handlerFqn
                    + " (target " + target.getName() + "); using the first (" + describe(first) + ")");
            return Optional.of(first.shape());
        }
        return Optional.of(matches.getFirst().shape());
    }

    private boolean assignable(Class<?> target, String onFqn) {
        try {
            Class<?> on = loader.loadClass(onFqn);
            return on.isAssignableFrom(target) || target.isAssignableFrom(on);
        } catch (ClassNotFoundException e) {
            report.warn("customHandlers 'on' type " + onFqn + " cannot be loaded; ignoring the declaration match");
            return false;
        }
    }

    private static String describe(HandlerConfig config) {
        return config.onEmpty() ? "the catch-all declaration" : "on " + String.join(", ", config.on());
    }
}
