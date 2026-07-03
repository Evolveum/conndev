/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.dev;

import org.identityconnectors.framework.common.objects.ObjectClassInfo;

import java.util.Collection;

/**
 * Object-class view of the framework schema model consumed by {@link ConnDevObjectClassSerializer}.
 * Implemented by the framework object-class definition (conndev {@code BaseObjectClassDefinition} and
 * its per-connector equivalents), so the development-mode export is derived from the one schema model
 * instead of being re-mapped from the raw protocol source.
 */
public interface ConnDevObjectClassSource {

    /** The ConnId side of the object class. */
    ObjectClassInfo connId();

    /**
     * Where the object class lives in the remote system: the resource endpoint for REST/SCIM, the
     * table for SQL. {@code null} when unknown.
     */
    default String locator() {
        return null;
    }

    /** Namespace of the object class (SCIM schema URN, SQL schema name); {@code null} when unknown. */
    default String namespace() {
        return null;
    }

    /** The framework attribute definitions of this object class. */
    Collection<? extends ConnDevAttributeSource> attributes();
}
