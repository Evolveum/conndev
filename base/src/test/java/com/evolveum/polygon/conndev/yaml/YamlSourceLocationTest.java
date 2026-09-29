/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.yaml;

import com.evolveum.polygon.conndev.api.ContextLookup;
import com.evolveum.polygon.conndev.concepts.DefinitionValue;
import com.evolveum.polygon.conndev.concepts.SourceLocation;
import com.evolveum.polygon.conndev.schema.BaseSchemaBuilder;
import org.identityconnectors.framework.spi.Configuration;
import org.identityconnectors.framework.spi.Connector;
import org.testng.annotations.Test;

import java.lang.reflect.Field;

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

    /**
     * The self-captured values — the ones the builders record via {@code SourceLocation.capture()}
     * (as opposed to the {@code DefinitionValue}s the binder passes explicitly) — must also carry
     * the YAML key's position: the {@code run(...)} wrappers around the builder invocations force
     * the capture to the driving key instead of {@code SourceLocation.UNKNOWN}. No development mode
     * is enabled: the override must win on its own.
     */
    @Test
    @SuppressWarnings("rawtypes")
    public void selfCapturedValuesCarryTheYamlKeyLocation() {
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

        // attribute("password") self-captures its name inside the factory; the capture is forced
        // to the 'password:' key (line 5), not the enclosing 'attributes:' block (line 4).
        var password = builder.objectClass("Widget").attribute("password");
        var remoteName = locationOf(password, "remoteName");
        assertEquals(remoteName.name(), "inline document");
        assertEquals(remoteName.line(), 5);
        assertEquals(remoteName.column(), 7);
        assertNotEquals(remoteName, SourceLocation.UNKNOWN);

        // Same for the object class name, self-captured inside the objectClass(...) factory,
        // forced to the 'Widget:' key (line 2).
        var widget = builder.objectClass("Widget");
        var className = locationOf(widget, "name");
        assertEquals(className.name(), "inline document");
        assertEquals(className.line(), 2);
        assertEquals(className.column(), 3);
        assertNotEquals(className, SourceLocation.UNKNOWN);
    }

    /** Reads the {@link SourceLocation} of the named {@code DefinitionValue} field (walking the class hierarchy). */
    private static SourceLocation locationOf(Object target, String fieldName) {
        Class<?> clazz = target.getClass();
        while (clazz != null) {
            try {
                Field field = clazz.getDeclaredField(fieldName);
                field.setAccessible(true);
                return ((DefinitionValue<?>) field.get(target)).location();
            } catch (NoSuchFieldException e) {
                clazz = clazz.getSuperclass();
            } catch (IllegalAccessException e) {
                throw new IllegalStateException("Cannot read field '" + fieldName + "' on " + target.getClass(), e);
            }
        }
        throw new IllegalStateException("Field '" + fieldName + "' not found on " + target.getClass());
    }
}
