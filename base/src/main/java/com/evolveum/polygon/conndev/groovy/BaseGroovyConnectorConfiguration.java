/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.groovy;

import com.evolveum.polygon.conndev.config.GroovyConfiguration;
import org.identityconnectors.framework.common.objects.ConnectorMessages;
import org.identityconnectors.framework.spi.Configuration;
import org.identityconnectors.framework.spi.ConfigurationProperty;

public abstract class BaseGroovyConnectorConfiguration implements Configuration, GroovyConfiguration {

    private ConnectorMessages messages;

    private GroovyContext groovyContext;

    private Boolean developmentMode = false;

    private Boolean groovyDisabled = false;

    @Override
    public ConnectorMessages getConnectorMessages() {
        return messages;
    }

    @Override
    public void setConnectorMessages(ConnectorMessages messages) {
        this.messages = messages;
    }

    @ConfigurationProperty(groupMessageKey = "conndev.developmentMode", required = false, order=5000)
    public Boolean getDevelopmentMode() {
        return developmentMode;
    }

    public void setDevelopmentMode(Boolean developmentMode) {
        this.developmentMode = developmentMode;
    }

    @ConfigurationProperty(groupMessageKey = "conndev.developmentMode", required = false, order=5010)
    public Boolean getGroovyDisabled() {
        return groovyDisabled;
    }

    public void setGroovyDisabled(Boolean groovyDisabled) {
        this.groovyDisabled = groovyDisabled;
    }

    public GroovyContext groovyContext() {
        if (groovyContext == null) {
            synchronized (this) {
                if (groovyContext == null) {
                    groovyContext = new GroovyContext(Boolean.TRUE.equals(groovyDisabled));
                }
            }
        }
        return groovyContext;
    }

    @Override
    public void validate() {

    }
}
