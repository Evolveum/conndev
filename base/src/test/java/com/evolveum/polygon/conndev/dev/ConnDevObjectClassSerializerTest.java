/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.dev;

import com.evolveum.polygon.conndev.api.ContextLookup;
import com.evolveum.polygon.conndev.concepts.RetrievableContext;
import com.evolveum.polygon.conndev.schema.BaseSchema;
import com.evolveum.polygon.conndev.schema.BaseSchemaBuilder;
import org.identityconnectors.framework.common.objects.*;
import org.identityconnectors.framework.spi.Configuration;
import org.identityconnectors.framework.spi.Connector;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

/**
 * Verifies that the development-mode export is derived from the one framework schema model
 * ({@link BaseSchema}): the serializer reads only the model (native names, locator, ConnId flags,
 * reference info) and emits the uniform {@code conndev_ObjectClass} objects.
 */
public class ConnDevObjectClassSerializerTest {

    private static final class StubConnector implements Connector {
        @Override public Configuration getConfiguration() { return null; }
        @Override public void init(Configuration c) { }
        @Override public void dispose() { }
    }

    private static final ContextLookup NOOP_CONTEXT = new ContextLookup() {
        @Override
        public <T extends RetrievableContext> T get(Class<T> contextType) {
            throw new IllegalStateException("No context registered for " + contextType);
        }
    };

    private BaseSchema schema() {
        var builder = new BaseSchemaBuilder(StubConnector.class, NOOP_CONTEXT);

        var user = builder.objectClass("user");
        user.locator("users").namespace("public");
        var id = user.attribute("id");
        id.connId().name(Uid.NAME).type(String.class);
        id.nativeType("INT");
        id.required(true).updatable(false).creatable(false);
        var name = user.attribute("name");
        name.connId().type(String.class);
        var lastLogin = user.attribute("last_login");
        lastLogin.connId().type(String.class);
        lastLogin.nativeType("TIMESTAMP");
        var manager = user.reference("manager_id");
        manager.objectClass("user")
                .role(AttributeInfo.RoleInReference.SUBJECT)
                .subtype("fk_user_manager")
                .referencedAttribute("id");

        // dev object classes live in the same schema, but are not part of the export
        for (var info : ConnDevSchema.objectClassInfos()) {
            builder.defineObjectClass(info);
        }
        return builder.build();
    }

    @Test
    public void derivesExportFromBaseSchema() {
        var objects = ConnDevObjectClassSerializer.serializeAll(schema().objectClasses());

        assertEquals(objects.size(), 1);
        var user = objects.getFirst();
        assertEquals(user.getObjectClass().getObjectClassValue(), "conndev_ObjectClass");
        assertEquals(user.getName().getNameValue(), "user");
        assertEquals(user.getUid().getUidValue(), "user");
        assertEquals(value(user, "locator"), "users");
        assertEquals(value(user, "namespace"), "public");

        var names = user.getAttributeByName("attributes").getValue().stream()
                .map(e -> string((EmbeddedObject) e, "name"))
                .collect(Collectors.toSet());
        assertEquals(names, Set.of("id", "name", "last_login", "manager_id"));
    }

    @Test
    public void emitsConnIdFlagsSparselyAndKeepsNativeNamesAndTypes() {
        var user = ConnDevObjectClassSerializer.serializeAll(schema().objectClasses()).getFirst();

        // the model maps "id" to __UID__, but the export keeps the original framework view
        var id = attribute(user, "id");
        assertEquals(string(id, "type"), "INT");
        assertEquals(single(id, "required"), Boolean.TRUE);
        assertEquals(single(id, "creatable"), Boolean.FALSE);
        assertEquals(single(id, "updateable"), Boolean.FALSE);

        var lastLogin = attribute(user, "last_login");
        assertEquals(string(lastLogin, "type"), "TIMESTAMP");

        // plain attribute with defaults: type from ConnId, no flags emitted
        var name = attribute(user, "name");
        assertEquals(string(name, "type"), "string");
        assertNull(AttributeUtil.find("required", name.getAttributes()));
        assertNull(AttributeUtil.find("creatable", name.getAttributes()));
        assertNull(AttributeUtil.find("referencedObjectClass", name.getAttributes()));
    }

    @Test
    public void serializesReferencesFromTheModel() {
        var user = ConnDevObjectClassSerializer.serializeAll(schema().objectClasses()).getFirst();

        var manager = attribute(user, "manager_id");
        assertEquals(string(manager, "type"), "reference");
        assertEquals(string(manager, "referencedObjectClass"), "user");
        assertEquals(string(manager, "referencedAttribute"), "id");
        assertEquals(string(manager, "reference"), "fk_user_manager");
        assertEquals(string(manager, "role"), "subject");
    }

    private static EmbeddedObject attribute(ConnectorObject object, String name) {
        List<Object> attributes = object.getAttributeByName("attributes").getValue();
        return attributes.stream().map(EmbeddedObject.class::cast)
                .filter(e -> name.equals(string(e, "name")))
                .findFirst().orElseThrow(() -> new AssertionError("No attribute named " + name));
    }

    private static String value(ConnectorObject object, String name) {
        var attribute = object.getAttributeByName(name);
        return attribute == null ? null : AttributeUtil.getStringValue(attribute);
    }

    private static String string(EmbeddedObject object, String name) {
        var attribute = AttributeUtil.find(name, object.getAttributes());
        return attribute == null ? null : AttributeUtil.getStringValue(attribute);
    }

    private static Object single(EmbeddedObject object, String name) {
        return AttributeUtil.getSingleValue(AttributeUtil.find(name, object.getAttributes()));
    }
}
