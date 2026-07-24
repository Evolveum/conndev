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

    /** The framework attribute definitions of this object class. */
    Collection<? extends ConnDevAttributeSource> attributes();

    /**
     * Extension point for protocol-specific export blocks (e.g. {@code scim}, {@code sql}) - called by
     * {@link ConnDevObjectClassSerializer} right before {@link ConnDevObjectClass#build()}. The default
     * is a no-op; implementations call
     * {@link ConnDevObjectClass#protocolSpecific(String, java.util.Collection)} to add their own named
     * block(s). This interface and the serializer stay protocol-agnostic.
     */
    default void contribute(ConnDevObjectClass target) {
    }
}
