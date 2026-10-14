/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.groovy;

import com.evolveum.polygon.conndev.api.ContextLookup;
import com.evolveum.polygon.conndev.schema.BaseSchemaBuilder;
import com.evolveum.polygon.conndev.yaml.GroovyScriptCompiler;
import com.evolveum.polygon.conndev.yaml.YamlSchemaLoader;
import org.identityconnectors.framework.common.exceptions.ConfigurationException;
import org.identityconnectors.framework.spi.Configuration;
import org.identityconnectors.framework.spi.Connector;
import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The {@code groovyDisabled} connector configuration option: every Groovy loading entry point —
 * the shared {@link GroovyContext} shell, the Groovy schema loader, YAML-embedded Groovy
 * fragments — is rejected with a {@link ConfigurationException} naming the option, while
 * Groovy-free paths (plain YAML) keep working.
 */
public class GroovyDisabledTest {

    private static final class TestConfiguration extends BaseGroovyConnectorConfiguration {
    }

    /** No-op stub — only needed so the schema builder has a connector to ask about. */
    private static final class StubConnector implements Connector {
        @Override public Configuration getConfiguration() { return null; }
        @Override public void init(Configuration c) { }
        @Override public void dispose() { }
    }

    private static final ContextLookup NOOP_CONTEXT = ContextLookup.none();

    private static BaseSchemaBuilder schemaBuilder() {
        return new BaseSchemaBuilder(StubConnector.class, NOOP_CONTEXT);
    }

    private static TestConfiguration disabledConfiguration() {
        var configuration = new TestConfiguration();
        configuration.setGroovyDisabled(true);
        return configuration;
    }

    @Test
    public void groovyDisabledDefaultsToFalse() {
        assertThat(new TestConfiguration().getGroovyDisabled()).isFalse();
    }

    /** Positive control: with the option unset, the shared context loads Groovy as before. */
    @Test
    public void enabledByDefault_contextLoadsGroovySchema() {
        var configuration = new TestConfiguration();
        var loader = new GroovySchemaLoader(configuration.groovyContext(), schemaBuilder());
        loader.load("objectClass('User') { }");

        assertThat(loader.build().objectClass("User")).isNotNull();
    }

    @Test
    public void disabledContextRejectsShellCreation() {
        assertThatThrownBy(() -> new GroovyContext(true).createShell())
                .isInstanceOf(ConfigurationException.class)
                .hasMessage(GroovyContext.GROOVY_DISABLED_MESSAGE);
    }

    @Test
    public void noArgContextIsUnaffected() {
        assertThat(new GroovyContext().createShell()).isNotNull();
    }

    @Test
    public void disabledConfigurationRejectsGroovySchemaLoader() {
        var configuration = disabledConfiguration();

        assertThatThrownBy(() -> new GroovySchemaLoader(configuration.groovyContext(), schemaBuilder()))
                .isInstanceOf(ConfigurationException.class)
                .hasMessage(GroovyContext.GROOVY_DISABLED_MESSAGE);
    }

    @Test
    public void disabledConfigurationRejectsScriptCompilation() {
        var configuration = disabledConfiguration();
        var compiler = new GroovyScriptCompiler(configuration.groovyContext());

        assertThatThrownBy(() -> compiler.compile("return 1 + 1"))
                .isInstanceOf(ConfigurationException.class)
                .hasMessage(GroovyContext.GROOVY_DISABLED_MESSAGE);
    }

    /** A YAML document whose {@code implementation} block carries Groovy is rejected at load. */
    @Test
    public void disabledConfigurationRejectsYamlWithGroovyFragments() {
        var configuration = disabledConfiguration();
        var loader = new YamlSchemaLoader(schemaBuilder(), configuration.groovyContext());

        assertThatThrownBy(() -> loader.load("""
                objectClasses:
                  Widget:
                    attributes:
                      label:
                        json:
                          type: string
                          implementation:
                            deserialize: |
                              return "d:" + value.asText()
                """))
                .isInstanceOf(ConfigurationException.class)
                .hasMessage(GroovyContext.GROOVY_DISABLED_MESSAGE);
    }

    /** Pure YAML without any Groovy fragment still loads — only Groovy use is rejected. */
    @Test
    public void disabledConfigurationStillAllowsPureYaml() {
        var configuration = disabledConfiguration();
        var builder = schemaBuilder();
        var loader = new YamlSchemaLoader(builder, configuration.groovyContext());
        loader.load("""
                objectClasses:
                  Widget:
                    attributes:
                      label:
                        json:
                          type: string
                """);
        builder.applyStructuralRules();

        assertThat(loader.build().objectClass("Widget")).isNotNull();
    }
}
