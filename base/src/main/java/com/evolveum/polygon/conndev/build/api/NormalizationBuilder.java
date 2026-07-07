/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */

package com.evolveum.polygon.conndev.build.api;

import com.evolveum.polygon.conndev.annotations.Script;
import groovy.lang.Closure;
import groovy.lang.DelegatesTo;

/**
 * Builder for normalizing attribute values during object retrieval.
 *
 * <p>Normalization transforms fetched data, for example splitting a single-valued attribute
 * that contains comma-separated values into multiple single values, or rewriting UIDs/names
 * based on processed data.</p>
 *
 * @see RewriteContext
 */
public interface NormalizationBuilder {

    /**
     * Configures normalization to split a single-value attribute into multiple values.
     *
     * <p>This creates multiple ConnId objects from a single object. UIDs and names
     * may need to be recomputed via {@link #rewriteUid} and {@link #rewriteName}.</p>
     *
     * @param attribute the attribute to split into multiple values
     * @return this normalizer for chaining
     */
    NormalizationBuilder toSingleValue(String attribute);

    /**
     * Configures a closure to rewrite UIDs based on normalized values.
     *
     * <p>The closure receives a {@link RewriteContext} with the original data and
     * the processed (normalized) value, allowing computation of derived UIDs.</p>
     *
     * @param implementation a closure that computes new UID values from normalized data
     * @return this normalizer for chaining
     */
    NormalizationBuilder rewriteUid(@DelegatesTo(value = RewriteContext.class) @Script.Runtime Closure<?> implementation);

    /**
     * Configures a closure to rewrite names based on normalized values.
     *
     * @param implementation a closure that computes new name values from normalized data
     * @return this normalizer for chaining
     */
    NormalizationBuilder rewriteName(@DelegatesTo(value = RewriteContext.class) @Script.Runtime Closure<?> implementation);

    /**
     * Configures a closure to restore UIDs to their original values before normalization.
     *
     * @param implementation a closure that restores UID values from the context
     * @return this normalizer for chaining
     */
    NormalizationBuilder restoreUid(@DelegatesTo(value = RewriteContext.class) @Script.Runtime Closure<?> implementation);

    /**
     * Configures a closure to restore names to their original values before normalization.
     *
     * @param implementation a closure that restores name values from the context
     * @return this normalizer for chaining
     */
    NormalizationBuilder restoreName(@DelegatesTo(value = RewriteContext.class) @Script.Runtime Closure<?> implementation);

    /**
     * Context object passed to normalization closures.
     *
     * @param original the original value from the remote system (before normalization)
     * @param value    the normalized value after {@code toSingleValue} is applied
     */
    record RewriteContext(String original, Object value) {
        /**
         * Returns the original remote value before normalization.
         *
         * @return the original value
         */
        public String getOriginal() {
            return original();
        }

        /**
         * Returns the value after normalization.
         *
         * @return the normalized value
         */
        public Object getValue() {
            return value;
        }
    }
}
