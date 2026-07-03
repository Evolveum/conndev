/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.dev;

import org.identityconnectors.framework.common.objects.AttributeInfo;

/**
 * Attribute view of the framework schema model consumed by {@link ConnDevObjectClassSerializer}.
 * Implemented by the framework attribute definition (conndev {@code BaseAttributeDefinition} and its
 * per-connector equivalents), so the development-mode export is derived from the one schema model
 * instead of being re-mapped from the raw protocol source.
 */
public interface ConnDevAttributeSource {

    /** The original (native) attribute name, as the remote system knows it. */
    String remoteName();

    /** The ConnId side of the attribute (type, flags, reference info). */
    AttributeInfo connId();

    /**
     * The native protocol type as declared by the remote system (e.g. SCIM {@code dateTime}, SQL
     * {@code TIMESTAMP}) when it carries more information than the mapped ConnId type; {@code null}
     * to fall back to the ConnId type.
     */
    default String nativeType() {
        return null;
    }

    /** The attribute of the referenced object class a reference points to (e.g. a FK target column). */
    default String referencedAttribute() {
        return null;
    }
}
