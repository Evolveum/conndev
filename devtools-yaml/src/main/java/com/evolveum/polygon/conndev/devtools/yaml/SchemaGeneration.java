/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.devtools.yaml;

import com.evolveum.polygon.conndev.devtools.yaml.codegen.jsonschema.JsonSchemaGenerator;
import com.evolveum.polygon.conndev.devtools.yaml.codegen.pydantic.PydanticGenerator;
import com.evolveum.polygon.conndev.devtools.yaml.config.ShapeFileConfig;
import com.evolveum.polygon.conndev.devtools.yaml.config.ShapeFileConfig.EntryConfig;
import com.evolveum.polygon.conndev.devtools.yaml.scan.BindingScanner;
import com.evolveum.polygon.conndev.devtools.yaml.scan.GapReport;
import com.evolveum.polygon.conndev.devtools.yaml.scan.HandlerShapes;
import com.evolveum.polygon.conndev.devtools.yaml.scan.JavadocReader;
import com.evolveum.polygon.conndev.devtools.yaml.shape.DelegatedObjectShape;
import com.evolveum.polygon.conndev.devtools.yaml.shape.DocumentShape;
import com.evolveum.polygon.conndev.devtools.yaml.shape.ListShape;
import com.evolveum.polygon.conndev.devtools.yaml.shape.MapShape;
import com.evolveum.polygon.conndev.devtools.yaml.shape.ObjectShape;
import com.evolveum.polygon.conndev.devtools.yaml.shape.ShapeMerger;
import com.evolveum.polygon.conndev.devtools.yaml.shape.UnionShape;
import com.evolveum.polygon.conndev.devtools.yaml.shape.YamlProperty;
import com.evolveum.polygon.conndev.devtools.yaml.shape.YamlShape;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * The generation facade: loads the project's shape file, scans its entry builders with the
 * runtime-faithful {@link BindingScanner}, merges the slots that bind onto the same YAML key,
 * resolves the declarative delegate shapes, applies the explicit-null allowances, and writes
 * the two artifacts — the JSONSchema ({@code <document>-yaml.schema.json}) and the Pydantic
 * module ({@code <pythonModule>.py}) — into the output directory.
 *
 * <p>Any gap the scanner reports (an unresolvable binding, a handler without a declared shape,
 * …) fails the build when {@code failOnGaps} is set — the shape file must stay in lockstep with
 * the builders.
 */
public final class SchemaGeneration {

    private static final Logger LOG = Logger.getLogger(SchemaGeneration.class.getName());

    private final ShapeFileConfig config;
    private final ClassLoader loader;
    private final List<Path> sourceRoots;
    private final Path outputDir;
    private final boolean failOnGaps;
    private final String generatedBy;
    private final GapReport report = new GapReport();

    private SchemaGeneration(ShapeFileConfig config, ClassLoader loader, List<Path> sourceRoots,
                             Path outputDir, boolean failOnGaps, String generatedBy) {
        this.config = config;
        this.loader = loader;
        this.sourceRoots = List.copyOf(sourceRoots);
        this.outputDir = outputDir;
        this.failOnGaps = failOnGaps;
        this.generatedBy = generatedBy;
    }

    /** The parameters of one generation run. */
    public static final class Parameters {

        private ShapeFileConfig config;
        private ClassLoader loader;
        private List<Path> sourceRoots = List.of();
        private Path outputDir;
        private boolean failOnGaps = true;
        private String generatedBy = "conndev-yaml";

        public Parameters config(ShapeFileConfig config) {
            this.config = config;
            return this;
        }

        public Parameters loader(ClassLoader loader) {
            this.loader = loader;
            return this;
        }

        public Parameters sourceRoots(List<Path> sourceRoots) {
            this.sourceRoots = List.copyOf(sourceRoots);
            return this;
        }

        public Parameters outputDir(Path outputDir) {
            this.outputDir = outputDir;
            return this;
        }

        public Parameters failOnGaps(boolean failOnGaps) {
            this.failOnGaps = failOnGaps;
            return this;
        }

        public Parameters generatedBy(String generatedBy) {
            this.generatedBy = generatedBy;
            return this;
        }

        public Generated generate() {
            return new SchemaGeneration(config, loader, sourceRoots, outputDir, failOnGaps, generatedBy).run();
        }
    }

    /** The written artifacts. */
    public record Generated(Path jsonSchema, Path pydanticModule) {
    }

    private Generated run() {
        BindingScanner scanner = new BindingScanner(loader, new JavadocReader(sourceRoots),
                new HandlerShapes(config.customHandlers(), loader, report), config, report);

        LinkedHashMap<String, YamlProperty> topLevel = new LinkedHashMap<>();
        for (EntryConfig entry : config.entries()) {
            YamlShape value = entry.type() == null ? null : scanner.shapeOf(load(entry.type(), "entries." + entry.key()));
            if (entry.shape() != null) {
                value = value == null ? entry.shape() : ShapeMerger.merge(value, entry.shape(), entry.key());
            }
            if (value == null) {
                throw new IllegalStateException("Entry '" + entry.key() + "' has neither a loadable 'type' nor a 'shape'");
            }
            YamlShape top = entry.mapOf() ? new MapShape(value, List.of(), "entry:" + entry.key(), null, false) : value;
            YamlProperty property = YamlProperty.builder()
                    .key(entry.key())
                    .shape(top)
                    .description(entry.description())
                    .build();
            // The slots that bind onto the same YAML key merge into one closed value.
            YamlProperty existing = topLevel.get(entry.key());
            topLevel.put(entry.key(), existing == null ? property : YamlProperty.builder()
                    .key(entry.key())
                    .shape(ShapeMerger.merge(existing.shape(), property.shape(), entry.key()))
                    .description(firstNonEmpty(existing.description(), entry.description()))
                    .build());
        }
        for (Map.Entry<String, EntryConfig> extra : config.extraEntries().entrySet()) {
            EntryConfig entry = extra.getValue();
            topLevel.put(extra.getKey(), YamlProperty.builder()
                    .key(extra.getKey())
                    .shape(entry.shape())
                    .description(entry.description())
                    .build());
        }

        LinkedHashMap<String, YamlProperty> resolved = new LinkedHashMap<>();
        List<String> patterns = config.nullablePaths();
        for (Map.Entry<String, YamlProperty> top : topLevel.entrySet()) {
            YamlProperty property = top.getValue();
            YamlShape overridden = applyOverrides(property.shape(), List.of(top.getKey()), config.overrides());
            property = property.toBuilder().shape(overridden).build();
            YamlProperty nulled = applyNullable(property, List.of(top.getKey()), patterns);
            resolved.put(top.getKey(), nulled.toBuilder().shape(resolve(nulled.shape(), top.getKey(), scanner)).build());
        }

        List<String> required = new ArrayList<>();
        if (resolved.containsKey("objectClasses")) {
            required.add("objectClasses");
        }
        DocumentShape document = new DocumentShape(config.document(), resolved, required, generatedBy);

        for (String warning : report.warnings()) {
            LOG.warning(warning);
        }
        if (report.hasErrors()) {
            StringBuilder sb = new StringBuilder("The conndev-yaml generation found gaps; the shape file must stay in lockstep with the builders:\n");
            for (String error : report.errors()) {
                sb.append("  - ").append(error).append('\n');
            }
            if (failOnGaps) {
                throw new IllegalStateException(sb.toString());
            }
            LOG.warning(sb.toString());
        }

        try {
            Files.createDirectories(outputDir);
            Path json = outputDir.resolve(config.document() + "-yaml.schema.json");
            Files.writeString(json, new JsonSchemaGenerator(document, config).generate());
            Path python = outputDir.resolve(config.pythonModule() + ".py");
            Files.writeString(python, new PydanticGenerator(document, config).generate());
            LOG.info("Wrote " + json);
            LOG.info("Wrote " + python);
            return new Generated(json, python);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot write the generated artifacts to " + outputDir, e);
        }
    }

    private Class<?> load(String fqn, String position) {
        try {
            return Class.forName(fqn, false, loader);
        } catch (ClassNotFoundException | LinkageError e) {
            // A LinkageError (e.g. a missing class in the type's hierarchy) is a classpath gap
            // just like a missing type; report it instead of letting it escape as a JVM error.
            report.error("Cannot load " + fqn + " (" + position + ") through the project classloader: " + e.getMessage());
            throw new IllegalStateException("Cannot load " + fqn + " (" + position + ")", e);
        }
    }

    /**
     * Replaces every {@link DelegatedObjectShape} with the merge of its declared properties and
     * the scanned bindings of its delegate types, resolving nested delegates (a delegate's own
     * sub-shapes may carry further declarative nodes).
     */
    private YamlShape resolve(YamlShape shape, String position, BindingScanner scanner) {
        if (shape instanceof DelegatedObjectShape delegate) {
            ObjectShape merged = new ObjectShape(new LinkedHashMap<>(delegate.declared().properties()),
                    delegate.declared().sourceType(), delegate.description(), delegate.deprecated(), delegate.declared().name());
            for (String fqn : delegate.delegateTypes()) {
                Map<String, YamlProperty> scanned = scanner.bindingsOf(load(fqn, position + ".inherit"));
                ShapeMerger.mergeDelegates(merged, new LinkedHashMap<>(scanned), position);
            }
            return resolveObjectProperties(merged, position, scanner);
        }
        if (shape instanceof ObjectShape o) {
            return resolveObjectProperties(o, position, scanner);
        }
        if (shape instanceof ListShape l) {
            return new ListShape(resolve(l.item(), position + "[*]", scanner), l.source(), l.description(), l.deprecated());
        }
        if (shape instanceof MapShape m) {
            return new MapShape(resolve(m.value(), position + ".*", scanner), m.restrictedKeys(), m.source(),
                    m.description(), m.deprecated(), m.nullableValue());
        }
        if (shape instanceof UnionShape u) {
            List<YamlShape> alternatives = new ArrayList<>();
            for (YamlShape alternative : u.alternatives()) {
                alternatives.add(resolve(alternative, position, scanner));
            }
            return new UnionShape(alternatives, u.source(), u.description(), u.deprecated());
        }
        return shape;
    }

    private YamlShape resolveObjectProperties(ObjectShape shape, String position, BindingScanner scanner) {
        LinkedHashMap<String, YamlProperty> properties = new LinkedHashMap<>();
        for (YamlProperty property : shape.properties().values()) {
            properties.put(property.key(),
                    property.toBuilder().shape(resolve(property.shape(), position + "." + property.key(), scanner)).build());
        }
        return new ObjectShape(properties, shape.sourceType(), shape.description(), shape.deprecated(), shape.name());
    }

    /**
     * Replaces the shapes at the dotted paths declared in the shape file's {@code overrides}
     * section (with {@code *} for a map key or a list index). The replacement is declared — it
     * may itself carry delegates — and is resolved like every other shape.
     */
    private YamlShape applyOverrides(YamlShape shape, List<String> path, Map<String, YamlShape> overrides) {
        if (overrides.isEmpty()) {
            return shape;
        }
        for (Map.Entry<String, YamlShape> override : overrides.entrySet()) {
            if (matches(override.getKey().split("\\."), path)) {
                return override.getValue();
            }
        }
        if (shape instanceof ObjectShape o) {
            LinkedHashMap<String, YamlProperty> properties = new LinkedHashMap<>();
            for (YamlProperty property : o.properties().values()) {
                properties.put(property.key(),
                        property.toBuilder().shape(applyOverrides(property.shape(), append(path, property.key()), overrides)).build());
            }
            return new ObjectShape(properties, o.sourceType(), o.description(), o.deprecated(), o.name());
        }
        if (shape instanceof MapShape m) {
            YamlShape value = applyOverrides(m.value(), append(path, "*"), overrides);
            return value == m.value() ? m : new MapShape(value, m.restrictedKeys(), m.source(), m.description(),
                    m.deprecated(), m.nullableValue());
        }
        if (shape instanceof ListShape l) {
            YamlShape item = applyOverrides(l.item(), append(path, "*"), overrides);
            return item == l.item() ? l : new ListShape(item, l.source(), l.description(), l.deprecated());
        }
        return shape;
    }

    /**
     * Applies the shape file's explicit-null allowances (dotted paths, {@code *} for a map key or
     * a list index) onto the property flags and the map entry-value nullability.
     */
    private YamlProperty applyNullable(YamlProperty property, List<String> path, List<String> patterns) {
        YamlShape shape = applyNullableShape(property.shape(), path, patterns);
        return property.toBuilder()
                .shape(shape)
                .nullable(property.nullable() || matches(patterns, path))
                .build();
    }

    private YamlShape applyNullableShape(YamlShape shape, List<String> path, List<String> patterns) {
        if (shape instanceof ObjectShape o) {
            LinkedHashMap<String, YamlProperty> properties = new LinkedHashMap<>();
            for (YamlProperty property : o.properties().values()) {
                properties.put(property.key(),
                        applyNullable(property, append(path, property.key()), patterns));
            }
            return new ObjectShape(properties, o.sourceType(), o.description(), o.deprecated(), o.name());
        }
        if (shape instanceof MapShape m) {
            YamlShape value = applyNullableShape(m.value(), append(path, "*"), patterns);
            return new MapShape(value, m.restrictedKeys(), m.source(), m.description(), m.deprecated(),
                    m.nullableValue() || matches(patterns, append(path, "*")));
        }
        if (shape instanceof ListShape l) {
            if (matches(patterns, append(path, "*"))) {
                report.warn("Nullable path " + String.join(".", path) + ".* targets a list item; "
                        + "list-item nullability is not supported - ignoring it");
            }
            return new ListShape(applyNullableShape(l.item(), append(path, "*"), patterns),
                    l.source(), l.description(), l.deprecated());
        }
        return shape;
    }

    /** Whether any pattern (literal segments or {@code *}) matches the path. */
    private static boolean matches(List<String> patterns, List<String> path) {
        for (String pattern : patterns) {
            if (matches(pattern.split("\\."), path)) {
                return true;
            }
        }
        return false;
    }

    private static boolean matches(String[] pattern, List<String> path) {
        if (pattern.length != path.size()) {
            return false;
        }
        for (int i = 0; i < pattern.length; i++) {
            if (!"*".equals(pattern[i]) && !pattern[i].equals(path.get(i))) {
                return false;
            }
        }
        return true;
    }

    private static List<String> append(List<String> path, String segment) {
        List<String> result = new ArrayList<>(path);
        result.add(segment);
        return result;
    }

    private static String firstNonEmpty(String a, String b) {
        return (a == null || a.isEmpty()) ? b : a;
    }
}
