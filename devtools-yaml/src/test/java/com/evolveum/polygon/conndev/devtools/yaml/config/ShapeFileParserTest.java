/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.devtools.yaml.config;

import com.evolveum.polygon.conndev.devtools.yaml.shape.MapShape;
import org.testng.annotations.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The shape file parses into the configuration model. */
public class ShapeFileParserTest {

    @Test
    public void theFixtureFileParses() throws IOException {
        Path file = extractToTemp();

        ShapeFileConfig config = ShapeFileParser.parse(file);

        assertThat(config.document()).isEqualTo("fixture");
        assertThat(config.pythonModule()).isEqualTo("fixture_yaml_schema");
        assertThat(config.pythonDocumentClass()).isEqualTo("ConnectorYamlDocument");
        assertThat(config.entries()).hasSize(2);
        assertThat(config.entries().getFirst().key()).isEqualTo("objectClasses");
        assertThat(config.entries().getFirst().mapOf()).isTrue();
        assertThat(config.extraEntries()).containsOnlyKeys("relationships");
        assertThat(config.extraEntries().get("relationships").shape()).isInstanceOf(MapShape.class);
        assertThat(config.customHandlers()).hasSize(1);
        assertThat(config.customHandlers().getFirst().handler())
                .isEqualTo("com.evolveum.polygon.conndev.devtools.yaml.fixtures.FixtureHandler");
        assertThat(config.valueParserShape("com.evolveum.polygon.conndev.devtools.yaml.fixtures.FixtureTypeParser"))
                .isNotNull();
        assertThat(config.nameFor("com.evolveum.polygon.conndev.devtools.yaml.fixtures.FixtureOperationsBuilder"))
                .isEqualTo("Operations");
        assertThat(config.nullablePaths()).containsExactly("relationships.*.subject.class");
    }

    @Test
    public void anUnsupportedApiVersionIsRejected() throws IOException {
        Path file = Files.createTempFile("shapes", ".yaml");
        Files.writeString(file, "apiVersion: conndev-yaml-shape/v0\ndocument: fixture\n");

        assertThatThrownBy(() -> ShapeFileParser.parse(file))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("apiVersion");
    }

    @Test
    public void aMissingEntryIsRejected() throws IOException {
        Path file = Files.createTempFile("shapes", ".yaml");
        Files.writeString(file, "apiVersion: conndev-yaml-shape/v1\ndocument: fixture\n");

        assertThatThrownBy(() -> ShapeFileParser.parse(file))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("entry");
    }

    @Test
    public void anEntryNeedsATypeOrAShape() throws IOException {
        Path file = Files.createTempFile("shapes", ".yaml");
        Files.writeString(file, """
                apiVersion: conndev-yaml-shape/v1
                document: fixture
                entries:
                  - key: objectClasses
                    binding: object
                """);

        assertThatThrownBy(() -> ShapeFileParser.parse(file))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("'type'");
    }

    private static Path extractToTemp() throws IOException {
        try (InputStream in = ShapeFileParserTest.class.getResourceAsStream("/shapefile/shapes.yaml")) {
            if (in == null) {
                throw new IllegalStateException("Missing test resource /shapefile/shapes.yaml");
            }
            Path file = Files.createTempFile("shapes", ".yaml");
            Files.copy(in, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            return file;
        }
    }
}
