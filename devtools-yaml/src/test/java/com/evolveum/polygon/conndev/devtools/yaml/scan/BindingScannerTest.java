/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.devtools.yaml.scan;

import com.evolveum.polygon.conndev.devtools.yaml.config.ShapeDsl;
import com.evolveum.polygon.conndev.devtools.yaml.config.ShapeFileConfig;
import com.evolveum.polygon.conndev.devtools.yaml.config.ShapeFileConfig.HandlerConfig;
import com.evolveum.polygon.conndev.devtools.yaml.fixtures.FixtureAttributeBuilder;
import com.evolveum.polygon.conndev.devtools.yaml.fixtures.FixtureHandler;
import com.evolveum.polygon.conndev.devtools.yaml.fixtures.FixtureMapValue;
import com.evolveum.polygon.conndev.devtools.yaml.fixtures.FixtureSubclassBuilder;
import com.evolveum.polygon.conndev.devtools.yaml.fixtures.FixtureTypeParser;
import com.evolveum.polygon.conndev.devtools.yaml.shape.LeafShape;
import com.evolveum.polygon.conndev.devtools.yaml.shape.LeafShape.LeafType;
import com.evolveum.polygon.conndev.devtools.yaml.shape.MapShape;
import com.evolveum.polygon.conndev.devtools.yaml.shape.ObjectShape;
import com.evolveum.polygon.conndev.devtools.yaml.shape.PathShape;
import com.evolveum.polygon.conndev.devtools.yaml.shape.ScriptShape;
import com.evolveum.polygon.conndev.devtools.yaml.shape.YamlProperty;
import com.evolveum.polygon.conndev.devtools.yaml.shape.YamlShape;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** The scanner must derive exactly the bindings the runtime binder would. */
public class BindingScannerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ShapeFileConfig config;
    private BindingScanner scanner;
    private GapReport report;

    @BeforeMethod
    public void setUp() {
        report = new GapReport();
        config = new ShapeFileConfig.Builder()
                .document("fixture")
                .addEntry(new ShapeFileConfig.EntryConfig("objectClasses", "default",
                        FixtureAttributeBuilder.class.getName(), "object", null, null))
                .addHandler(new HandlerConfig(FixtureHandler.class.getName(), List.of(), dsl("""
                        {
                          "type": "object",
                          "properties": {
                            "method": { "type": "string", "enum": ["GET", "POST"] },
                            "path": { "type": "string" }
                          },
                          "required": ["path"]
                        }
                        """)))
                .addValueParser(FixtureTypeParser.class.getName(),
                        dsl("{\"type\": \"string\", \"description\": \"a fixture type\"}"))
                .build();
        scanner = new BindingScanner(getClass().getClassLoader(), new JavadocReader(List.of()),
                new HandlerShapes(config.customHandlers(), getClass().getClassLoader(), report), config, report);
    }

    private static YamlShape dsl(String json) {
        return ShapeDsl.parse(MAPPER.readTree(json), "test");
    }

    @Test
    public void scansAllBindingKinds() {
        Map<String, YamlProperty> bindings = scanner.bindingsOf(FixtureAttributeBuilder.class);

        assertThat(bindings).containsKeys(
                "description", "required", "multiValued", "jsonType", "resolution", "nativeType",
                "json", "attributes", "jsonPath", "endpoint", "body", "connId");
        assertThat(bindings).doesNotContainKeys("multiValuedFlag", "connIdValue", "helper");
    }

    @Test
    public void leavesCarryTheCoercedTypes() {
        Map<String, YamlProperty> bindings = scanner.bindingsOf(FixtureAttributeBuilder.class);

        assertThat(leaf(bindings, "description").type()).isEqualTo(LeafType.STRING);
        assertThat(leaf(bindings, "required").type()).isEqualTo(LeafType.BOOLEAN);
        assertThat(leaf(bindings, "multiValued").type()).isEqualTo(LeafType.BOOLEAN);
        LeafShape resolution = leaf(bindings, "resolution");
        assertThat(resolution.type()).isEqualTo(LeafType.STRING);
        assertThat(resolution.enumValues()).containsExactly("PER_OBJECT", "BATCH");
    }

    @Test
    public void deprecatedBindingsAreKeptAndFlagged() {
        YamlProperty jsonType = scanner.bindingsOf(FixtureAttributeBuilder.class).get("jsonType");

        assertThat(jsonType.deprecated()).isTrue();
        assertThat(leaf(jsonType).type()).isEqualTo(LeafType.STRING);
    }

    @Test
    public void valueParserTakesTheDeclaredShape() {
        YamlProperty nativeType = scanner.bindingsOf(FixtureAttributeBuilder.class).get("nativeType");

        assertThat(nativeType.shape()).isInstanceOf(LeafShape.class);
        assertThat(((LeafShape) nativeType.shape()).description()).isEqualTo("a fixture type");
    }

    @Test
    public void subBuilderBindsItsOwnShape() {
        YamlProperty json = scanner.bindingsOf(FixtureAttributeBuilder.class).get("json");

        assertThat(json.shape()).isInstanceOf(ObjectShape.class);
        ObjectShape shape = (ObjectShape) json.shape();
        assertThat(shape.sourceType()).isEqualTo("com.evolveum.polygon.conndev.devtools.yaml.fixtures.FixtureMappingBuilder");
        assertThat(shape.properties()).containsKeys("name", "type");
    }

    @Test
    public void mapFactoryBindsANameMapOfItsReturnType() {
        YamlProperty attributes = scanner.bindingsOf(FixtureAttributeBuilder.class).get("attributes");

        assertThat(attributes.shape()).isInstanceOf(MapShape.class);
        MapShape shape = (MapShape) attributes.shape();
        assertThat(shape.value()).isInstanceOf(ObjectShape.class);
        assertThat(((ObjectShape) shape.value()).sourceType()).isEqualTo(FixtureMapValue.class.getName());
    }

    @Test
    public void pathBindingBindsAPathShape() {
        YamlProperty jsonPath = scanner.bindingsOf(FixtureAttributeBuilder.class).get("jsonPath");

        assertThat(jsonPath.shape()).isInstanceOf(PathShape.class);
    }

    @Test
    public void customHandlerTakesItsDeclaredShape() {
        YamlProperty endpoint = scanner.bindingsOf(FixtureAttributeBuilder.class).get("endpoint");

        assertThat(endpoint.shape()).isInstanceOf(ObjectShape.class);
        ObjectShape shape = (ObjectShape) endpoint.shape();
        assertThat(shape.properties()).containsKeys("method", "path");
        assertThat(shape.properties().get("path").required()).isTrue();
        assertThat(leaf(shape.properties().get("method")).enumValues()).containsExactly("GET", "POST");
    }

    @Test
    public void scriptedClosureBindsAScriptShape() {
        YamlProperty body = scanner.bindingsOf(FixtureAttributeBuilder.class).get("body");

        assertThat(body.shape()).isInstanceOf(ScriptShape.class);
        assertThat(((ScriptShape) body.shape()).expression()).isFalse();
    }

    @Test
    public void aScriptedClosureIsShadowedByAYamlKeyOfTheSameName() {
        YamlProperty connId = scanner.bindingsOf(FixtureAttributeBuilder.class).get("connId");

        assertThat(connId).isNotNull();
        assertThat(connId.source()).isEqualTo(
                "com.evolveum.polygon.conndev.devtools.yaml.fixtures.FixtureAttributeBuilder#connIdValue");
    }

    @Test
    public void theHierarchyIsWalked() {
        Map<String, YamlProperty> bindings = scanner.bindingsOf(FixtureSubclassBuilder.class);

        assertThat(bindings).containsKeys("description", "readOnly", "operations", "normalize", "extension");
    }

    @Test
    public void aHandlerWithoutADeclaredShapeIsAGap() {
        GapReport gapReport = new GapReport();
        ShapeFileConfig gaplessConfig = new ShapeFileConfig.Builder()
                .document("fixture")
                .addEntry(new ShapeFileConfig.EntryConfig("objectClasses", "default",
                        FixtureAttributeBuilder.class.getName(), "object", null, null))
                .build();
        BindingScanner gapScanner = new BindingScanner(getClass().getClassLoader(), new JavadocReader(List.of()),
                new HandlerShapes(gaplessConfig.customHandlers(), getClass().getClassLoader(), gapReport), gaplessConfig, gapReport);

        gapScanner.bindingsOf(FixtureAttributeBuilder.class);

        assertThat(gapReport.hasErrors()).isTrue();
        assertThat(gapReport.errors().getFirst()).contains(FixtureHandler.class.getName());
    }

    @Test
    public void theReportStaysCleanOnAFullyDeclaredScan() {
        scanner.shapeOf(FixtureAttributeBuilder.class);

        assertThat(report.errors()).isEmpty();
    }

    private static LeafShape leaf(Map<String, YamlProperty> bindings, String key) {
        return (LeafShape) bindings.get(key).shape();
    }

    private static LeafShape leaf(YamlProperty property) {
        return (LeafShape) property.shape();
    }
}
