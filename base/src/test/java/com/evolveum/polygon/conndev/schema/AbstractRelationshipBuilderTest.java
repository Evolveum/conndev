/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.schema;

import com.evolveum.polygon.conndev.api.ContextLookup;
import com.evolveum.polygon.conndev.build.api.RelationshipBuilder;
import com.evolveum.polygon.conndev.concepts.DefinitionValue;
import com.evolveum.polygon.conndev.concepts.GroovyClosures;
import com.evolveum.polygon.conndev.concepts.SourceLocation;
import groovy.lang.Closure;
import org.identityconnectors.framework.common.exceptions.ConfigurationException;
import org.identityconnectors.framework.common.objects.AttributeInfo;
import org.identityconnectors.framework.common.objects.ConnectorObjectReference;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.spi.Configuration;
import org.identityconnectors.framework.spi.Connector;
import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests {@link AbstractRelationshipBuilder#afterExecution()} — the post-execution wiring of the
 * subject/object cross-references — and the configuration errors it reports when a participant
 * declares no reference attribute (instead of surfacing a {@code NullPointerException}).
 */
public class AbstractRelationshipBuilderTest {

    // ========================================================================
    // Test doubles
    // ========================================================================

    /** Stub connector used as parameter requirement for BaseSchemaBuilder. */
    private static final class StubConnector implements Connector {
        @Override
        public Configuration getConfiguration() {
            return null;
        }

        @Override
        public void init(Configuration c) {
        }

        @Override
        public void dispose() {
        }
    }

    /** Marker for the reference attribute: satisfies the relationship participant's B contract. */
    private interface TestReferenceBuilder extends
            RelationshipBuilder.Reference<TestReferenceBuilder, TestReferenceBuilder, BaseAttributeDefinition> {
    }

    /** Concrete reference attribute builder for the participants. */
    private static final class TestReferenceAttributeBuilder extends BaseAttributeBuilder<
            TestReferenceAttributeBuilder, TestReferenceBuilder, TestReferenceBuilder, BaseAttributeDefinition>
            implements TestReferenceBuilder {

        TestReferenceAttributeBuilder(BaseObjectClassDefinitionBuilder parent, DefinitionValue<String> name) {
            super(parent, name);
        }
    }

    /** Participant double: creates the concrete reference attribute and stores it for the no-arg accessor. */
    private static final class TestParticipantBuilder extends BaseParticipantBuilder<TestReferenceBuilder, BaseAttributeDefinition> {

        private final BaseObjectClassDefinitionBuilder targetClass;

        TestParticipantBuilder(BaseObjectClassDefinitionBuilder targetClass) {
            // The relationship-builder parent argument is unused by the behavior under test here.
            super(null, targetClass, SourceLocation.UNKNOWN);
            this.targetClass = targetClass;
        }

        @Override
        public TestReferenceBuilder attribute(String name) {
            var existing = attribute();
            if (existing != null) {
                return existing;
            }
            var created = new TestReferenceAttributeBuilder(targetClass, DefinitionValue.from(name, SourceLocation.UNKNOWN));
            // Relationship reference attributes are typed as ConnectorObjectReference by the builders.
            created.connId().type(DefinitionValue.detected(ConnectorObjectReference.class));
            setReferenceAttribute(created);
            return created;
        }
    }

    /** The relationship under test. */
    private static final class TestRelationshipBuilder extends AbstractRelationshipBuilder<TestReferenceBuilder, BaseAttributeDefinition> {

        private final BaseSchemaBuilder schemaBuilder;

        TestRelationshipBuilder(String name) {
            // The parent's schema builder is not used by the behavior under test here.
            super(name, schemaBuilder());
            this.schemaBuilder = schemaBuilder();
        }

        @Override
        @SuppressWarnings("unchecked")
        public Participant<TestReferenceBuilder, BaseAttributeDefinition> subject(String objectClass, Closure<?> closure) {
            if (subject == null) {
                subject = new TestParticipantBuilder((BaseObjectClassDefinitionBuilder) schemaBuilder.objectClass(objectClass));
            }
            return GroovyClosures.callAndReturnDelegate(closure, subject);
        }

        @Override
        @SuppressWarnings("unchecked")
        public Participant<TestReferenceBuilder, BaseAttributeDefinition> object(String objectClass, Closure<?> closure) {
            if (object == null) {
                object = new TestParticipantBuilder((BaseObjectClassDefinitionBuilder) schemaBuilder.objectClass(objectClass));
            }
            return GroovyClosures.callAndReturnDelegate(closure, object);
        }
    }

    // ========================================================================
    // Helpers
    // ========================================================================

    private static BaseSchemaBuilder schemaBuilder() {
        return new BaseSchemaBuilder(StubConnector.class, ContextLookup.none());
    }

    private static TestObjectClass objectClass(String name) {
        return new TestObjectClass(schemaBuilder(), DefinitionValue.from(new ObjectClass(name), SourceLocation.UNKNOWN));
    }

    private static AttributeInfo connIdInfo(TestReferenceAttributeBuilder attribute) {
        return attribute.build().connId();
    }

    // ========================================================================
    // afterExecution wiring
    // ========================================================================

    @Test
    public void afterExecution_wiresCrossReferencesWhenBothParticipantsDeclareAttributes() {
        var relationship = new TestRelationshipBuilder("userGroups");
        relationship.subject = new TestParticipantBuilder(objectClass("User"));
        relationship.object = new TestParticipantBuilder(objectClass("Group"));

        var subjectAttribute = (TestReferenceAttributeBuilder) relationship.subject.attribute("groups");
        var objectAttribute = (TestReferenceAttributeBuilder) relationship.object.attribute("members");

        relationship.afterExecution();

        var subjectInfo = connIdInfo(subjectAttribute);
        assertThat(subjectInfo.getReferencedObjectClassName()).isEqualTo("Group");
        assertThat(subjectInfo.getRoleInReference()).isEqualTo(AttributeInfo.RoleInReference.SUBJECT.toString());

        var objectInfo = connIdInfo(objectAttribute);
        assertThat(objectInfo.getReferencedObjectClassName()).isEqualTo("User");
        assertThat(objectInfo.getRoleInReference()).isEqualTo(AttributeInfo.RoleInReference.OBJECT.toString());
    }

    @Test
    public void afterExecution_doesNotThrowWhenOnlyOneParticipantIsDeclared() {
        // A relationship with a single participant block has no cross-reference to wire;
        // the post-execution wiring is a no-op for it.
        var relationship = new TestRelationshipBuilder("userGroups");
        relationship.subject = new TestParticipantBuilder(objectClass("User"));
        relationship.subject.attribute("groups");

        assertThatCode(relationship::afterExecution).doesNotThrowAnyException();
    }

    // ========================================================================
    // Missing reference attribute
    // ========================================================================

    @Test
    public void afterExecution_throwsConfigurationExceptionWhenSubjectDeclaresNoAttribute() {
        var relationship = new TestRelationshipBuilder("userGroups");
        relationship.subject = new TestParticipantBuilder(objectClass("User"));
        relationship.object = new TestParticipantBuilder(objectClass("Group"));
        relationship.object.attribute("members");

        assertThatThrownBy(relationship::afterExecution)
                .isInstanceOf(ConfigurationException.class)
                .hasMessageContaining("Relationship 'userGroups'")
                .hasMessageContaining("the subject participant ('User') declares no reference attribute");
    }

    @Test
    public void afterExecution_throwsConfigurationExceptionWhenObjectDeclaresNoAttribute() {
        var relationship = new TestRelationshipBuilder("userGroups");
        relationship.subject = new TestParticipantBuilder(objectClass("User"));
        relationship.subject.attribute("groups");
        relationship.object = new TestParticipantBuilder(objectClass("Group"));

        assertThatThrownBy(relationship::afterExecution)
                .isInstanceOf(ConfigurationException.class)
                .hasMessageContaining("Relationship 'userGroups'")
                .hasMessageContaining("the object participant ('Group') declares no reference attribute");
    }

    // ========================================================================
    // owner(...) before the attribute is declared
    // ========================================================================

    @Test
    public void owner_beforeAttributeDeclared_throwsConfigurationException() {
        var participant = new TestParticipantBuilder(objectClass("User"));

        assertThatThrownBy(() -> participant.owner(true))
                .isInstanceOf(ConfigurationException.class)
                .hasMessageContaining("owner() requires the reference attribute to be declared first");
    }

    @Test
    public void owner_afterAttributeDeclared_returnsTheReferenceAttribute() {
        var participant = new TestParticipantBuilder(objectClass("User"));
        var attribute = participant.attribute("members");

        assertThat(participant.owner(true)).isSameAs(attribute);
        assertThat(participant.owner()).isTrue();
    }

    // ========================================================================
    // Error message
    // ========================================================================

    @Test
    public void missingAttributeMessage_containsLocationWhenKnown() {
        var message = AbstractRelationshipBuilder.missingAttributeMessage(
                "userGroups", "object", "Group", SourceLocation.from("relationships.schema.groovy", 12, 0));

        assertThat(message)
                .isEqualTo("Relationship 'userGroups': the object participant ('Group') declares no reference attribute; "
                        + "declare one with attribute('<name>') inside object('Group') [at relationships.schema.groovy:12]");
    }

    @Test
    public void missingAttributeMessage_omitsLocationWhenUnknown() {
        var message = AbstractRelationshipBuilder.missingAttributeMessage(
                "userGroups", "object", "Group", SourceLocation.UNKNOWN);

        assertThat(message)
                .isEqualTo("Relationship 'userGroups': the object participant ('Group') declares no reference attribute; "
                        + "declare one with attribute('<name>') inside object('Group')");
    }
}
