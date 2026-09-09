/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.yaml;

import com.evolveum.polygon.conndev.api.ContextLookup;
import com.evolveum.polygon.conndev.concepts.SourceLocation;
import com.evolveum.polygon.conndev.schema.BaseSchemaBuilder;
import org.identityconnectors.framework.spi.Configuration;
import org.identityconnectors.framework.spi.Connector;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotEquals;

/**
 * Proves the whole point of the location-aware engine: every {@code DefinitionValue} the YAML
 * front-end builds carries the position of the YAML statement (source name + 1-based line/column)
 * instead of {@code SourceLocation.UNKNOWN} (which is what the old POJO front-end produced).
 */
public class YamlSourceLocationTest {

    private static final class StubConnector implements Connector {
        @Override public Configuration getConfiguration() { return null; }
        @Override public void init(Configuration c) { }
        @Override public void dispose() { }
    }

    private static final ContextLookup NOOP_CONTEXT = ContextLookup.none();

    @Test
    @SuppressWarnings("rawtypes")
    public void definitionValuesCarryTheYamlLocation() {
        var builder = new BaseSchemaBuilder(StubConnector.class, NOOP_CONTEXT);
        var loader = new YamlSchemaLoader(builder);
        loader.load("""
                objectClasses:
                  Widget:
                    description: the widget
                    attributes:
                      password:
                        jsonType: string
                        creatable: false
                        connId:
                          name: __UID__
                          type: GuardedString
                """);

        var widget = builder.objectClass("Widget");
        var password = widget.attribute("password");

        // connId.name (line 9) and connId.type (line 10) carry the position of their YAML keys
        var nameDv = password.connId().name();
        assertEquals(nameDv.location().name(), "inline document");
        assertEquals(nameDv.location().line(), 9);
        assertEquals(nameDv.location().column(), 11);

        var typeDv = password.connId().type();
        assertEquals(typeDv.location().name(), "inline document");
        assertEquals(typeDv.location().line(), 10);
        assertEquals(typeDv.location().column(), 11);
        assertNotEquals(typeDv.location(), SourceLocation.UNKNOWN);
    }

    @Test
    @SuppressWarnings("rawtypes")
    public void locationReflectsTheLoadedResourceName() {
        var builder = new BaseSchemaBuilder(StubConnector.class, NOOP_CONTEXT);
        var loader = new YamlSchemaLoader(builder);
        // id -> connId: {name: UID} sits on line 23, column 11 of the fixture
        loader.loadFromResource("/schema/ForgejoMinimalUser.yaml");

        var id = builder.objectClass("User").attribute("id");
        var nameDv = id.connId().name();
        assertEquals(nameDv.location().name(), "/schema/ForgejoMinimalUser.yaml");
        assertEquals(nameDv.location().line(), 23);
        assertEquals(nameDv.location().column(), 11);
    }
}
