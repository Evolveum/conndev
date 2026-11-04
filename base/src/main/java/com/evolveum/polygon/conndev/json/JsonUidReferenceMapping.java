/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 */
package com.evolveum.polygon.conndev.json;

import com.evolveum.polygon.conndev.annotations.Yaml;
import org.identityconnectors.framework.common.objects.AttributeUtil;
import org.identityconnectors.framework.common.objects.ConnectorObjectBuilder;
import org.identityconnectors.framework.common.objects.ConnectorObjectReference;
import org.identityconnectors.framework.common.objects.ObjectClass;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.databind.node.StringNode;

import java.util.Set;

/** Maps one string UID or JSON object containing a UID. JsonAttributeMapping handles the enclosing collection. */
public class JsonUidReferenceMapping implements JsonValueMapping {
    private final ObjectClass target;
    private final String uidField;
    private final String nameField;

    protected JsonUidReferenceMapping(ObjectClass target, Options options) {
        if (target == null) {
            throw new IllegalArgumentException("referenceBy 'uid' requires a target objectClass");
        }
        this.target = target;
        uidField = options.uidField;
        nameField = options.nameField;
        if (uidField == null && nameField != null) {
            throw new IllegalArgumentException("nameFrom requires a uidFrom field");
        }
    }

    @Override public Class<?> connIdType() { return ConnectorObjectReference.class; }
    @Override public Class<? extends JsonNode> primaryWireType() { return uidField == null ? StringNode.class : ObjectNode.class; }
    @Override public Set<Class<? extends JsonNode>> supportedWireTypes() { return Set.of(primaryWireType()); }

    @Override
    public Object toConnIdValue(JsonNode value) {
        if (value == null || value.isNull() || hasNullUid(value)) {
            return null;
        }
        JsonNode raw = uidField == null ? value : value.get(uidField);
        if (!(raw instanceof StringNode text) || text.asString().isBlank()) {
            throw new IllegalArgumentException("Expected a nonblank string UID" + (uidField == null ? "" : " in field " + uidField));
        }
        String uid = decodeUid(text.asString());
        JsonNode name = nameField == null ? null : value.get(nameField);
        String label = name instanceof StringNode title && !title.asString().isBlank() ? title.asString() : uid;
        var identification = new ConnectorObjectBuilder().setObjectClass(target).setUid(uid).setName(label).buildIdentification();
        return new ConnectorObjectReference(identification);
    }

    private boolean hasNullUid(JsonNode value) {
        return uidField != null && value.path(uidField).isNull();
    }

    @Override
    public boolean includeArrayItem(JsonNode item) {
        if (item.isNull()) {
            throw new IllegalArgumentException("Null item in reference list for " + target.getObjectClassValue());
        }
        return !hasNullUid(item);
    }

    @Override
    public JsonNode toWireValue(Object value) {
        if (!(value instanceof ConnectorObjectReference ref) || !target.equals(ref.getValue().getObjectClass())) {
            throw new IllegalArgumentException("Expected a reference to " + target.getObjectClassValue());
        }
        var uid = AttributeUtil.getUidAttribute(ref.getValue().getAttributes());
        if (uid == null || uid.getUidValue().isBlank()) {
            throw new IllegalArgumentException("Reference has no UID");
        }
        String id = uid.getUidValue();
        if (uidField == null) {
            return NODE_FACTORY.stringNode(id);
        }
        return NODE_FACTORY.objectNode().put(uidField, encodeUid(id));
    }

    /** Protocol mappings can convert an object field to and from the UID. */
    protected String decodeUid(String value) { return value; }
    protected String encodeUid(String uid) { return uid; }

    /** Optional fields for objects carrying a UID and display name. */
    public static class Options {
        private String uidField;
        private String nameField;

        @Yaml.Key public void uidFrom(String field) { uidField = fieldName(field); }
        @Yaml.Key public void nameFrom(String field) { nameField = fieldName(field); }

        protected boolean hasUidField() { return uidField != null; }

        private static String fieldName(String value) {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException("Reference field names must be nonblank strings");
            }
            return value;
        }

        public JsonUidReferenceMapping build(ObjectClass target) {
            return new JsonUidReferenceMapping(target, this);
        }
    }
}
