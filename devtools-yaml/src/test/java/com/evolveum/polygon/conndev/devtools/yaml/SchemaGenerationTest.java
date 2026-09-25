/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.devtools.yaml;

import com.evolveum.polygon.conndev.devtools.yaml.config.ShapeFileParser;
import com.evolveum.polygon.conndev.devtools.yaml.config.ShapeFileConfig;
import org.testng.annotations.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** End-to-end: shape file + fixture builders to the two artifacts. */
public class SchemaGenerationTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private Path outputDir;

    @org.testng.annotations.BeforeMethod
    public void setUp() throws IOException {
        outputDir = Files.createTempDirectory("conndev-yaml-test");
    }

    @Test
    public void generatesBothArtifacts() throws IOException {
        SchemaGeneration.Generated generated = generate(defaultShapeFile(), outputDir);

        assertThat(generated.jsonSchema()).exists();
        assertThat(generated.pydanticModule()).exists();
        assertThat(generated.jsonSchema().getFileName().toString()).isEqualTo("fixture-yaml.schema.json");
        assertThat(generated.pydanticModule().getFileName().toString()).isEqualTo("fixture_yaml_schema.py");
    }

    @Test
    public void theJsonSchemaIsAClosedStrictDocument() throws IOException {
        SchemaGeneration.Generated generated = generate(defaultShapeFile(), outputDir);
        JsonNode root = MAPPER.readTree(generated.jsonSchema());

        assertThat(root.get("$schema").asText()).isEqualTo("https://json-schema.org/draft/2020-12/schema");
        assertThat(root.get("additionalProperties").asBoolean()).isFalse();
        assertThat(root.get("required").toString()).isEqualTo("[\"objectClasses\"]");
        assertThat(root.get("properties").has("objectClasses")).isTrue();

        JsonNode objectClasses = root.get("properties").get("objectClasses");
        assertThat(objectClasses.get("type").asText()).isEqualTo("object");
        assertThat(objectClasses.get("additionalProperties").get("$ref").asText()).isEqualTo("#/$defs/ObjectClass");

        JsonNode defs = root.get("$defs");
        assertThat(defs.has("ObjectClass")).isTrue();
        JsonNode objectClass = defs.get("ObjectClass");
        assertThat(objectClass.get("additionalProperties").asBoolean()).isFalse();
        assertThat(objectClass.get("properties").has("description")).isTrue();
        assertThat(objectClass.get("properties").has("operations")).isTrue();
        // The operations slot merged into the same union (same key, both mapOf).
        assertThat(objectClass.get("properties").get("operations").get("$ref").asText()).isEqualTo("#/$defs/Operations");
        assertThat(defs.has("Operations")).isTrue();

        // The script leaf carries the x-script metadata.
        assertThat(objectClass.get("properties").get("normalize").get("x-script").get("kind").asText())
                .isEqualTo("block");
        // The handler shape is its own def (named after the handler class), referenced by the endpoint.
        JsonNode endpoint = defs.get("Operations").get("properties").get("endpoint");
        assertThat(endpoint.get("$ref").asText()).isEqualTo("#/$defs/Fixture");
        JsonNode endpointDef = defs.get("Fixture");
        assertThat(endpointDef.get("properties").get("method").get("enum").toString())
                .isEqualTo("[\"GET\",\"POST\"]");
        assertThat(endpointDef.get("properties").get("path").toString()).isNotNull();
    }

    @Test
    public void theNullablePathAllowsExplicitNulls() throws IOException {
        SchemaGeneration.Generated generated = generate(defaultShapeFile(), outputDir);
        JsonNode root = MAPPER.readTree(generated.jsonSchema());

        // relationships.*.subject.class is the declared nullable path.
        JsonNode relationships = root.get("properties").get("relationships");
        assertThat(relationships.get("additionalProperties").get("$ref").asText()).isEqualTo("#/$defs/Relationship");
        JsonNode participant = root.get("$defs").get("Participant");
        assertThat(participant.get("properties").get("class").get("type").toString())
                .isEqualTo("[\"string\",\"null\"]");
        // A property the shape file does not declare nullable stays plain.
        assertThat(participant.get("properties").get("class").has("anyOf")).isFalse();
        JsonNode objectClass = root.get("$defs").get("ObjectClass");
        assertThat(objectClass.get("properties").get("description").get("type").asText()).isEqualTo("string");
    }

    @Test
    public void thePydanticModuleKeepsTheMidpilotContract() throws IOException {
        SchemaGeneration.Generated generated = generate(defaultShapeFile(), outputDir);
        String python = Files.readString(generated.pydanticModule());

        assertThat(python).contains("from __future__ import annotations");
        assertThat(python).contains("class _Configuration(BaseModel):");
        assertThat(python).contains("model_config = ConfigDict(strict=True, extra=\"forbid\")");
        assertThat(python).contains("reject_explicit_null");
        assertThat(python).contains("class _AttributePath(_Configuration):");
        assertThat(python).contains("def _script(*, expression: bool = False, empty_body: bool = False) -> Any:");
        assertThat(python).contains("class ConnectorYamlDocument(_Configuration):");
        assertThat(python).contains("objectClasses: dict[str, _ObjectClass] | None = Field(default=None)");
        assertThat(python).contains("class _ObjectClass(_Configuration):");
        assertThat(python).contains("class _Operations(_Configuration):");
        assertThat(python).contains("normalize: str | None = _script()");
        // The keyword key 'class' (in relationships) gets the established alias.
        assertThat(python).contains("class_name: str | None = Field(default=None, alias=\"class\")");
        // The nullable path lands in a nullable_fields set.
        assertThat(python).contains("frozenset({\"class_name\"})");
    }

    @Test
    public void anOverrideReplacesANestedShape() throws IOException {
        Path shapeFile = Files.writeString(Files.createTempFile("shapes", ".yaml"), """
                apiVersion: conndev-yaml-shape/v1
                document: fixture
                entries:
                  - key: objectClasses
                    binding: mapOf
                    type: com.evolveum.polygon.conndev.devtools.yaml.fixtures.FixtureClassBuilder
                extraEntries:
                  relationships:
                    shape:
                      type: map
                      value:
                        type: object
                        name: Relationship
                        properties:
                          subject:
                            type: object
                            name: Participant
                            properties:
                              class:
                                type: string
                customHandlers:
                  - handler: com.evolveum.polygon.conndev.devtools.yaml.fixtures.FixtureHandler
                    shape:
                      type: object
                      properties:
                        path:
                          type: string
                      required: [path]
                overrides:
                  relationships.*.subject.class:
                    type: oneOf
                    items:
                      - type: string
                      - type: boolean
                """);
        SchemaGeneration.Generated generated = generate(shapeFile, outputDir);
        JsonNode root = MAPPER.readTree(generated.jsonSchema());

        JsonNode participant = root.get("$defs").get("Participant");
        JsonNode klass = participant.get("properties").get("class");
        assertThat(klass.get("oneOf").size()).isEqualTo(2);
        assertThat(klass.get("oneOf").get(0).get("type").asText()).isEqualTo("string");
        assertThat(klass.get("oneOf").get(1).get("type").asText()).isEqualTo("boolean");
    }

    @Test
    public void aGapFailsTheBuild() throws IOException {
        Path shapeFile = Files.writeString(Files.createTempFile("shapes", ".yaml"), """
                apiVersion: conndev-yaml-shape/v1
                document: fixture
                entries:
                  - key: objectClasses
                    binding: mapOf
                    type: com.evolveum.polygon.conndev.devtools.yaml.fixtures.FixtureClassBuilder
                """);

        assertThatThrownBy(() -> generate(shapeFile, outputDir))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("gaps");
    }

    @Test
    public void aGapDoesNotFailWhenFailOnGapsIsOff() throws IOException {
        Path shapeFile = Files.writeString(Files.createTempFile("shapes", ".yaml"), """
                apiVersion: conndev-yaml-shape/v1
                document: fixture
                entries:
                  - key: objectClasses
                    binding: mapOf
                    type: com.evolveum.polygon.conndev.devtools.yaml.fixtures.FixtureClassBuilder
                """);

        ShapeFileConfig config = ShapeFileParser.parse(shapeFile);
        SchemaGeneration.Generated generated = new SchemaGeneration.Parameters()
                .config(config)
                .loader(getClass().getClassLoader())
                .outputDir(outputDir)
                .failOnGaps(false)
                .generate();

        assertThat(generated.jsonSchema()).exists();
    }

    private SchemaGeneration.Generated generate(Path shapeFile, Path output) {
        return new SchemaGeneration.Parameters()
                .config(ShapeFileParser.parse(shapeFile))
                .loader(getClass().getClassLoader())
                .sourceRoots(List.of())
                .outputDir(output)
                .generatedBy("conndev-yaml-plugin (test)")
                .generate();
    }

    private static Path defaultShapeFile() throws IOException {
        try (InputStream in = SchemaGenerationTest.class.getResourceAsStream("/shapefile/shapes.yaml")) {
            if (in == null) {
                throw new IllegalStateException("Missing test resource /shapefile/shapes.yaml");
            }
            Path file = Files.createTempFile("shapes", ".yaml");
            Files.copy(in, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            return file;
        }
    }
}
