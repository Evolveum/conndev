/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.schema;

import com.evolveum.polygon.conndev.api.ContextLookup;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.Uid;
import org.identityconnectors.framework.spi.Configuration;
import org.identityconnectors.framework.spi.Connector;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertSame;

/**
 * Object class identity is the ConnId {@link ObjectClass}: registration and lookup are
 * case-insensitive, so {@code "User"} and {@code "user"} resolve to the same builder and
 * the same built definition, while the declared case is preserved in the ConnId type.
 */
public class ObjectClassIdentityTest {

    private static final class StubConnector implements Connector {
        @Override public Configuration getConfiguration() { return null; }
        @Override public void init(Configuration c) {}
        @Override public void dispose() {}
    }

    @Test
    public void objectClassRegistrationIsCaseInsensitive() {
        var builder = new BaseSchemaBuilder(StubConnector.class, ContextLookup.none());

        var fromCamelCase = builder.objectClass("User");
        var fromLowerCase = builder.objectClass("user");

        assertSame(fromCamelCase, fromLowerCase);
        var count = 0;
        for (var ignored : builder.allObjectClasses()) {
            count++;
        }
        assertEquals(count, 1);
    }

    @Test
    public void builtSchemaResolvesObjectClassCaseInsensitively() {
        var builder = new BaseSchemaBuilder(StubConnector.class, ContextLookup.none());
        builder.objectClass("User").attribute("id").connId().name(Uid.NAME).type(String.class);

        var schema = builder.build();

        assertSame(schema.objectClass("USER"), schema.objectClass("user"));
        assertSame(schema.objectClass("User"), schema.objectClass(new ObjectClass("user")));
        assertEquals(schema.objectClasses().size(), 1);
        assertEquals(schema.objectClass("user").objectClass(), new ObjectClass("User"));
        assertEquals(schema.objectClass("user").connId().getType(), "User");
    }
}
