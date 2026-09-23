/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.schema;

import com.evolveum.polygon.conndev.api.ContextLookup;
import com.evolveum.polygon.conndev.yaml.YamlSchemaLoader;
import org.identityconnectors.framework.common.objects.AttributeInfo;
import org.identityconnectors.framework.common.objects.Name;
import org.identityconnectors.framework.common.objects.Uid;
import org.identityconnectors.framework.spi.Configuration;
import org.identityconnectors.framework.spi.Connector;
import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** A same-named object class from {@code objectClass(name)} and {@code defineObjectClass(OC)} must reach the ConnId schema once. */
public class SchemaBuilderDefinitionMergeTest {

    private static final class StubConnector implements Connector {
        @Override public Configuration getConfiguration() { return null; }
        @Override public void init(Configuration c) {}
        @Override public void dispose() {}
    }

    @Test
    public void sameNamedObjectClassFromTwoSourcesYieldsOneConnIdEntry() {
        var builder = new BaseSchemaBuilder(StubConnector.class, ContextLookup.none());

        var discovered = builder.objectClass("User");
        discovered.attribute("id").connId().name(Uid.NAME).type(String.class);
        discovered.attribute("userName").connId().name(Name.NAME).type(String.class);
        discovered.attribute("displayName").connId().name("displayName").type(String.class);

        var scriptBuilder = new BaseSchemaBuilder(StubConnector.class, ContextLookup.none());
        var yamlLoader = new YamlSchemaLoader(scriptBuilder);
        yamlLoader.load("""
                objectClasses:
                  User:
                    attributes:
                      id:
                        connId:
                          name: __UID__
                      userPrincipalName:
                        connId:
                          name: __NAME__
                """);
        var parsed = yamlLoader.build();
        for (var definition : parsed.objectClasses()) {
            builder.defineObjectClass((BaseObjectClassDefinition) definition);
        }

        builder.applyStructuralRules();
        var schema = builder.build();

        var userClasses = schema.connIdSchema().getObjectClassInfo().stream()
                .filter(oci -> "User".equals(oci.getType()))
                .toList();
        assertThat(userClasses).hasSize(1);

        var attributeNames = userClasses.get(0).getAttributeInfo().stream()
                .map(AttributeInfo::getName)
                .toList();
        assertThat(attributeNames).contains(Name.NAME, Uid.NAME);
        assertThat(attributeNames).doesNotContain("displayName");
    }
}
