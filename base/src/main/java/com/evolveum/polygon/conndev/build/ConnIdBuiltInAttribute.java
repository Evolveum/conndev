/*
 * Copyright (c) 2026 Evolveum and contributors
 * 
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 * 
 */
package com.evolveum.polygon.conndev.build;

import com.evolveum.polygon.conndev.annotations.Groovy;
import org.identityconnectors.framework.common.objects.Name;
import org.identityconnectors.framework.common.objects.OperationalAttributes;
import org.identityconnectors.framework.common.objects.Uid;

import java.util.Arrays;

public enum ConnIdBuiltInAttribute {

    UID(Uid.NAME, String.class),
    NAME(Name.NAME, String.class),
    PASSWORD(OperationalAttributes.PASSWORD_NAME),
    ENABLE(OperationalAttributes.ENABLE_NAME),
    ENABLE_DATE(OperationalAttributes.ENABLE_DATE_NAME),
    DISABLE_DATE(OperationalAttributes.DISABLE_DATE_NAME),
    LOCK_OUT(OperationalAttributes.LOCK_OUT_NAME);

    private String connIdName;
    private Class<?> forcedType;

    ConnIdBuiltInAttribute(String name) {
        this(name, null);
    }

    ConnIdBuiltInAttribute(String name, Class<?> forcedType) {
        this.connIdName = name;
        this.forcedType = forcedType;
    }

    public String getConnIdName() {
        return connIdName;
    }

    public Class<?> getForcedType() {
        return forcedType;
    }

    public static ConnIdBuiltInAttribute findBuiltIn(String name) {
        return Arrays.stream(values()).filter(v -> v.connIdName.equals(name)).findFirst().orElse(null);
    }

    @Groovy.Convenience
    public interface Mixin {
        ConnIdBuiltInAttribute UID = ConnIdBuiltInAttribute.UID;
        ConnIdBuiltInAttribute NAME = ConnIdBuiltInAttribute.NAME;
        ConnIdBuiltInAttribute PASSWORD = ConnIdBuiltInAttribute.PASSWORD;
        ConnIdBuiltInAttribute ENABLE = ConnIdBuiltInAttribute.ENABLE;
        ConnIdBuiltInAttribute ENABLE_DATE = ConnIdBuiltInAttribute.ENABLE_DATE;
        ConnIdBuiltInAttribute DISABLE_DATE = ConnIdBuiltInAttribute.DISABLE_DATE;
        ConnIdBuiltInAttribute LOCK_OUT = ConnIdBuiltInAttribute.LOCK_OUT;
    }
}
