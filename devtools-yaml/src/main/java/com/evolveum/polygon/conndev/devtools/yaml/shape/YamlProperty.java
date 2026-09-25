/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.devtools.yaml.shape;

/**
 * A single YAML key of an {@link ObjectShape}: its shape plus the binding metadata the generators
 * need — whether the key is required, whether an explicit YAML {@code null} is accepted (the
 * runtime binder treats a null leaf as "leave the default untouched", and the shape file decides
 * how much of that leniency the generated artifacts expose), and the originating method for
 * traceability.
 */
public final class YamlProperty {

    private final String key;
    private final YamlShape shape;
    private final String description;
    private final boolean required;
    private final boolean nullable;
    private final boolean deprecated;
    private final String source;

    private YamlProperty(Builder b) {
        this.key = b.key;
        this.shape = b.shape;
        this.description = b.description;
        this.required = b.required;
        this.nullable = b.nullable;
        this.deprecated = b.deprecated;
        this.source = b.source;
    }

    public String key() {
        return key;
    }

    public YamlShape shape() {
        return shape;
    }

    public String description() {
        return description;
    }

    public boolean required() {
        return required;
    }

    /** Whether an explicit {@code null} value is accepted (not just an absent key). */
    public boolean nullable() {
        return nullable;
    }

    public boolean deprecated() {
        return deprecated;
    }

    /** The originating binding (e.g. {@code com.example.Builder#method}) for traceability. */
    public String source() {
        return source;
    }

    public Builder toBuilder() {
        Builder b = new Builder();
        b.key = key;
        b.shape = shape;
        b.description = description;
        b.required = required;
        b.nullable = nullable;
        b.deprecated = deprecated;
        b.source = source;
        return b;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private String key;
        private YamlShape shape;
        private String description;
        private boolean required;
        private boolean nullable;
        private boolean deprecated;
        private String source = "";

        public Builder key(String key) {
            this.key = key;
            return this;
        }

        public Builder shape(YamlShape shape) {
            this.shape = shape;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder required(boolean required) {
            this.required = required;
            return this;
        }

        public Builder nullable(boolean nullable) {
            this.nullable = nullable;
            return this;
        }

        public Builder deprecated(boolean deprecated) {
            this.deprecated = deprecated;
            return this;
        }

        public Builder source(String source) {
            this.source = source;
            return this;
        }

        public YamlProperty build() {
            if (key == null || key.isEmpty()) {
                throw new IllegalStateException("A property needs a YAML key");
            }
            if (shape == null) {
                throw new IllegalStateException("Property '" + key + "' needs a shape");
            }
            return new YamlProperty(this);
        }
    }

    @Override
    public String toString() {
        return key + ":" + shape;
    }
}
