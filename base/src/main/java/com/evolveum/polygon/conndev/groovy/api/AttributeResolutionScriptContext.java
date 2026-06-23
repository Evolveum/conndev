/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.groovy.api;

import com.evolveum.polygon.conndev.annotations.Groovy;
import org.identityconnectors.framework.common.objects.ConnectorObjectBuilder;

public interface AttributeResolutionScriptContext extends BaseScriptContext {

    @Groovy.Convenience
    default ConnectorObjectBuilder getValue() {
        return value();
    }

    ConnectorObjectBuilder value();

}
