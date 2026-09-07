/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.spi;

import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.identityconnectors.framework.common.objects.Attribute;
import org.identityconnectors.framework.common.objects.AttributeBuilder;
import org.identityconnectors.framework.common.objects.OperationOptions;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.expectThrows;

public class AttributeOperationRoutingTest {

    private static final OperationOptions OPTIONS = new OperationOptions(Map.of());

    @Test
    public void handlersReceiveOnlyItemsNotClaimedEarlier() {
        var first = new Handler("first", List.of("a"));
        var fallback = new Handler("fallback", List.of("a", "b"));
        var routing = new AttributeOperationRouting<Attribute>(List.of(
                AttributeBuilder.build("a", "1"), AttributeBuilder.build("b", "2")));

        assertEquals(names(routing.select(first, OPTIONS).supported()), List.of("a"));
        assertEquals(names(routing.select(fallback, OPTIONS).supported()), List.of("b"));
        assertEquals(first.seen, List.of("a", "b"));
        assertEquals(fallback.seen, List.of("b"));
        routing.requireComplete();
    }

    @Test
    public void unsupportedItemsAreReported() {
        var routing = new AttributeOperationRouting<Attribute>(
                List.of(AttributeBuilder.build("missing", "x")));
        expectThrows(ConnectorException.class, routing::requireComplete);
    }

    @Test
    public void handlerCannotClaimAnItemItWasNotGiven() {
        var requested = AttributeBuilder.build("requested", "x");
        var outside = AttributeBuilder.build("outside", "x");
        var routing = new AttributeOperationRouting<Attribute>(List.of(requested));
        var handler = new Handler("invalid", List.of()) {
            @Override
            public Capability<Attribute, AttributeOperationRoutingTest.Handler> canHandle(
                    Collection<Attribute> request, OperationOptions options) {
                return new Capability<>(this, List.of(outside));
            }
        };
        expectThrows(ConnectorException.class, () -> routing.select(handler, OPTIONS));
    }

    private static List<String> names(Collection<Attribute> attributes) {
        return attributes.stream().map(Attribute::getName).toList();
    }

    private static class Handler implements AttributeAwareOperationHandler<Attribute, Handler> {
        private final String name;
        private final List<String> supported;
        private List<String> seen = new ArrayList<>();

        private Handler(String name, List<String> supported) {
            this.name = name;
            this.supported = supported;
        }

        @Override
        public Capability<Attribute, Handler> canHandle(
                Collection<Attribute> request, OperationOptions options) {
            seen = names(request);
            return new Capability<>(this, request.stream()
                    .filter(attribute -> supported.contains(attribute.getName())).toList());
        }

        @Override
        public String toString() {
            return name;
        }
    }
}
