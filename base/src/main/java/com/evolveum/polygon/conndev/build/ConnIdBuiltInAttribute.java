package com.evolveum.polygon.conndev.build;

import com.evolveum.polygon.conndev.annotations.Groovy;
import org.identityconnectors.framework.common.objects.Name;
import org.identityconnectors.framework.common.objects.OperationalAttributes;
import org.identityconnectors.framework.common.objects.Uid;

public enum ConnIdBuiltInAttribute {

    UID(Uid.NAME),
    NAME(Name.NAME),
    PASSWORD(OperationalAttributes.PASSWORD_NAME),
    ENABLE(OperationalAttributes.ENABLE_NAME),
    ENABLE_DATE(OperationalAttributes.ENABLE_DATE_NAME),
    DISABLE_DATE(OperationalAttributes.DISABLE_DATE_NAME),
    LOCK_OUT(OperationalAttributes.LOCK_OUT_NAME);

    private String connIdName;

    ConnIdBuiltInAttribute(String name) {
        this.connIdName = name;
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
