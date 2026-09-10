/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.spi;

import com.evolveum.polygon.conndev.logging.ConnDevLog;
import org.identityconnectors.framework.common.objects.Attribute;
import org.identityconnectors.framework.common.objects.AttributeDelta;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Attaches orchestration detail events of a strategy handler to the operation entry currently
 * active on this thread (started by {@code ConnectorLog.runOperation} in
 * {@link ClassHandlerConnectorBase}).
 *
 * <p>Events are dropped silently when no entry is active, e.g. when a handler is invoked
 * outside of a wrapped ConnId operation.
 */
final class OperationTracing {

    /** Detail key: routing decision mapping handler labels to attribute names. */
    static final String ROUTING = "routing";

    /** Detail key: whether the original object state must be read. */
    static final String READ_ORIGINAL = "readOriginal";

    /** Detail key: uid of the object whose previous state was read. */
    static final String ORIGINAL_STATE = "originalState";

    /** Detail key: the subhandler about to be executed. */
    static final String EXECUTING = "executing";

    /** Detail key: cleanup handlers running before the primary deletion. */
    static final String CLEANUP = "cleanup";

    private OperationTracing() {
    }

    /**
     * Logs a detail on the active operation entry of the given facade, if any.
     *
     * @param log   the logging facade
     * @param key   the detail key
     * @param value the detail value
     */
    static void detail(ConnDevLog log, String key, Object value) {
        var entry = log.currentOperation();
        if (entry != null) {
            entry.detail(key, value);
        }
    }

    /**
     * Labels the given handlers by simple class name, suffixing duplicates with a 1-based
     * index so that handlers of the same class stay distinguishable.
     *
     * @param handlers the handlers, in execution order
     * @return a label per handler, aligned with the input order
     */
    static List<String> labels(Collection<?> handlers) {
        var counts = new HashMap<String, Integer>();
        for (var handler : handlers) {
            counts.merge(simpleName(handler), 1, Integer::sum);
        }
        var assigned = new HashMap<String, Integer>();
        var labels = new ArrayList<String>(handlers.size());
        for (var handler : handlers) {
            var name = simpleName(handler);
            labels.add(counts.get(name) > 1
                    ? name + "#" + assigned.merge(name, 1, Integer::sum)
                    : name);
        }
        return labels;
    }

    /**
     * Logs an {@code executing} detail announcing the given subhandler and its attributes.
     *
     * @param log        the logging facade
     * @param handler    the label of the subhandler (see {@link #labels})
     * @param attributes attribute names the subhandler applies to (may be empty)
     */
    static void executing(ConnDevLog log, String handler, Collection<String> attributes) {
        var kvs = new LinkedHashMap<String, Object>();
        kvs.put("handler", handler);
        if (!attributes.isEmpty()) {
            kvs.put("attributes", List.copyOf(attributes));
        }
        detail(log, EXECUTING, kvs);
    }

    /**
     * Builds the {@code routing} detail value: handler label to attribute names.
     *
     * @param labels            handler labels (see {@link #labels})
     * @param attributesPerHandler attribute names per handler, aligned with {@code labels}
     * @return the routing map in handler order
     */
    static Map<String, List<String>> routing(List<String> labels, List<List<String>> attributesPerHandler) {
        var map = new LinkedHashMap<String, List<String>>();
        for (var i = 0; i < labels.size(); i++) {
            map.put(labels.get(i), attributesPerHandler.get(i));
        }
        return map;
    }

    /**
     * Returns the names of the given attribute deltas.
     *
     * @param deltas the deltas
     * @return delta names in collection order
     */
    static List<String> deltaNames(Collection<AttributeDelta> deltas) {
        var names = new ArrayList<String>(deltas.size());
        deltas.forEach(delta -> names.add(delta.getName()));
        return names;
    }

    /**
     * Returns the names of the given attributes.
     *
     * @param attributes the attributes
     * @return attribute names in collection order
     */
    static List<String> attributeNames(Collection<Attribute> attributes) {
        var names = new ArrayList<String>(attributes.size());
        attributes.forEach(attribute -> names.add(attribute.getName()));
        return names;
    }

    private static String simpleName(Object handler) {
        return handler.getClass().getSimpleName();
    }
}
