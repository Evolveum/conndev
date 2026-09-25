/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.plugin;

import com.evolveum.polygon.conndev.devtools.yaml.SchemaGeneration;
import com.evolveum.polygon.conndev.devtools.yaml.config.ShapeFileParser;
import com.evolveum.polygon.conndev.devtools.yaml.config.ShapeFileConfig;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Scans the project's {@code @Yaml.*}-annotated entry builders (against the project's shape
 * file, by default {@code src/main/conndev-yaml/shapes.yaml}) and writes the JSONSchema and the
 * Pydantic module into the build output (default {@code target/conndev-yaml}). Bound to
 * {@code process-classes}: the entry builders must be compiled, and the artifacts are build
 * outputs — the developer copies them into the consuming project by hand.
 */
@Mojo(name = "generate", defaultPhase = LifecyclePhase.PROCESS_CLASSES, threadSafe = true,
        requiresDependencyResolution = ResolutionScope.COMPILE)
public class GenerateYamlSchemasMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project}", readonly = true)
    private MavenProject project;

    /** The shape file declaring the entries, handler shapes, parsers, names and nullability. */
    @Parameter(property = "conndevYaml.shapeFile",
            defaultValue = "${project.basedir}/src/main/conndev-yaml/shapes.yaml")
    private File shapeFile;

    /** Where the JSONSchema and the Pydantic module are written. */
    @Parameter(property = "conndevYaml.outputDirectory",
            defaultValue = "${project.build.directory}/conndev-yaml")
    private File outputDirectory;

    /** Fail the build on gaps (an unresolvable binding, a handler without a declared shape). */
    @Parameter(property = "conndevYaml.failOnGaps", defaultValue = "true")
    private boolean failOnGaps;

    /** The tool line stamped into the generated artifacts' headers. */
    @Parameter(property = "conndevYaml.generatedBy")
    private String generatedBy;

    @Override
    public void execute() throws MojoExecutionException {
        Path shapePath = shapeFile == null ? null : shapeFile.toPath();
        if (shapePath == null || !Files.isRegularFile(shapePath)) {
            getLog().info("conndev-yaml: no shape file at " + shapeFile + "; skipping generation");
            return;
        }
        ShapeFileConfig config = ShapeFileParser.parse(shapePath);
        getLog().info("conndev-yaml: generating the '" + config.document() + "' document from " + shapePath);
        try (URLClassLoader loader = projectClassLoader()) {
            SchemaGeneration.Generated generated = new SchemaGeneration.Parameters()
                    .config(config)
                    .loader(loader)
                    .sourceRoots(project.getCompileSourceRoots().stream().map(Path::of).toList())
                    .outputDir(outputDirectory.toPath())
                    .failOnGaps(failOnGaps)
                    .generatedBy(generatedBy == null ? "conndev-yaml-plugin" : generatedBy)
                    .generate();
            getLog().info("conndev-yaml: wrote " + generated.jsonSchema());
            getLog().info("conndev-yaml: wrote " + generated.pydanticModule());
        } catch (IOException e) {
            throw new MojoExecutionException("Cannot build the project classloader", e);
        } catch (IllegalStateException e) {
            throw new MojoExecutionException("conndev-yaml generation failed: " + e.getMessage(), e);
        }
    }

    /**
     * A classloader over the project's compile classpath: the scanned builders and their
     * {@code @Yaml.*} annotations are resolved from the project's realm, while the scanner
     * itself runs from the plugin's realm.
     */
    private URLClassLoader projectClassLoader() throws MojoExecutionException {
        List<String> elements;
        try {
            elements = project.getCompileClasspathElements();
        } catch (org.apache.maven.artifact.DependencyResolutionRequiredException e) {
            throw new MojoExecutionException("Cannot resolve the project compile classpath", e);
        }
        List<URL> urls = new ArrayList<>();
        for (String element : elements) {
            try {
                urls.add(Path.of(element).toUri().toURL());
            } catch (MalformedURLException e) {
                throw new MojoExecutionException("Invalid classpath element: " + element, e);
            }
        }
        return new URLClassLoader(urls.toArray(new URL[0]), getClass().getClassLoader());
    }
}
