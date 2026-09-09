/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.spi;

import com.evolveum.polygon.conndev.api.ContextLookup;
import com.evolveum.polygon.conndev.build.api.UpdateOperationBuilder;
import com.evolveum.polygon.conndev.concepts.RetrievableContext;
import com.evolveum.polygon.conndev.groovy.ConnectorContext;
import com.evolveum.polygon.conndev.schema.BaseSchema;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.identityconnectors.framework.common.objects.Attribute;
import org.identityconnectors.framework.common.objects.AttributeBuilder;
import org.identityconnectors.framework.common.objects.AttributeDelta;
import org.identityconnectors.framework.common.objects.AttributeDeltaBuilder;
import org.identityconnectors.framework.common.objects.ConnectorObject;
import org.identityconnectors.framework.common.objects.ConnectorObjectBuilder;
import org.identityconnectors.framework.common.objects.Name;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.OperationOptions;
import org.identityconnectors.framework.common.objects.Uid;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import static org.testng.Assert.*;

public class OperationStrategyHandlerTest {

    private static final ObjectClass ACCOUNT = new ObjectClass("account");
    private static final Uid UID = new Uid("1");
    private static final OperationOptions OPTIONS = new OperationOptions(Map.of());
    private static final ConnectorObject OBJECT = new ConnectorObjectBuilder()
            .setObjectClass(ACCOUNT).setUid(UID).setName("alice").build();

    @Test
    public void createRunsSelectedPrimaryThenAttributeHandlersInOneTransaction() {
        var fixture = new Fixture();
        var operation = new CreateOperationStrategyHandler(fixture.executor,
                List.of(fixture.creator(Name.NAME)), List.of(fixture.child("phones")));
        var result = operation.create(Set.of(
                new Name("alice"), AttributeBuilder.build("phones", "123")), OPTIONS);

        assertSame(result, OBJECT);
        assertEquals(fixture.events,
                List.of("begin", "create", "phones", "commit", "close"));
    }

    @Test
    public void createPreservesPrimaryHandlerPriority() {
        var fixture = new Fixture();
        var operation = new CreateOperationStrategyHandler(fixture.executor,
                List.of(fixture.creator(Name.NAME), fixture.creator(Name.NAME)), List.of());

        operation.create(Set.of(new Name("alice")), OPTIONS);

        assertEquals(fixture.events, List.of("begin", "create", "commit", "close"));
    }

    @Test
    public void unsupportedCreateFailsBeforeTransaction() {
        var fixture = new Fixture();
        var operation = new CreateOperationStrategyHandler(fixture.executor,
                List.of(fixture.creator(Name.NAME)), List.of());

        expectThrows(ConnectorException.class,
                () -> operation.create(Set.of(AttributeBuilder.build("unknown", "x")), OPTIONS));
        assertEquals(fixture.events, List.of());
    }

    @Test
    public void createFailureRollsBack() {
        var fixture = new Fixture();
        fixture.failAt = "phones";
        var operation = new CreateOperationStrategyHandler(fixture.executor,
                List.of(fixture.creator(Name.NAME)), List.of(fixture.child("phones")));

        expectThrows(IllegalStateException.class, () -> operation.create(Set.of(
                new Name("alice"), AttributeBuilder.build("phones", "123")), OPTIONS));
        assertEquals(fixture.events,
                List.of("begin", "create", "phones", "rollback", "close"));
    }

    @Test
    public void updateUsesFirstHandlerThenFallbackAndReadsInsideTransaction() {
        var fixture = new Fixture();
        var name = delta("name", "Alice");
        var title = delta("title", "Engineer");
        var operation = new UpdateOperationStrategyHandler(fixture, ACCOUNT, fixture.executor,
                List.of(fixture.updater("name", true, "name"),
                        fixture.updater("fallback", false, "name", "title")));

        assertEquals(operation.updateDelta(UID, Set.of(name, title), OPTIONS), Set.of(name, title));
        assertEquals(fixture.seenByFallback, List.of("title"));
        assertEquals(fixture.events,
                List.of("begin", "read", "name", "fallback", "commit", "close"));
    }

    @Test
    public void emptyUpdateDoesNotOpenTransaction() {
        var fixture = new Fixture();
        var operation = new UpdateOperationStrategyHandler(fixture, ACCOUNT, fixture.executor,
                List.of(fixture.updater("update", false, "name")));

        assertEquals(operation.updateDelta(UID, Set.of(), OPTIONS), Set.of());
        assertEquals(fixture.events, List.of());
    }

    @Test
    public void updateFailureRollsBackAndStopsLaterHandlers() {
        var fixture = new Fixture();
        fixture.failAt = "first";
        var operation = new UpdateOperationStrategyHandler(fixture, ACCOUNT, fixture.executor,
                List.of(fixture.updater("first", false, "name"),
                        fixture.updater("second", false, "title")));

        expectThrows(IllegalStateException.class, () -> operation.updateDelta(UID,
                Set.of(delta("name", "Alice"), delta("title", "Engineer")), OPTIONS));
        assertEquals(fixture.events, List.of("begin", "first", "rollback", "close"));
    }

    @Test
    public void deleteRunsCleanupBeforePrimaryAndRollsBackFailure() {
        var fixture = new Fixture();
        var operation = new DeleteOperationStrategyHandler(fixture.executor,
                List.of(fixture.deleter("parent")), List.of(
                        fixture.deleter("child"), fixture.deleter("junction")));

        operation.delete(UID, OPTIONS);
        assertEquals(fixture.events,
                List.of("begin", "child", "junction", "parent", "commit", "close"));

        fixture.events.clear();
        fixture.failAt = "junction";
        expectThrows(IllegalStateException.class, () -> operation.delete(UID, OPTIONS));
        assertEquals(fixture.events,
                List.of("begin", "child", "junction", "rollback", "close"));
    }

    private static AttributeDelta delta(String name, String value) {
        return new AttributeDeltaBuilder().setName(name).addValueToReplace(value).build();
    }

    private static <R> List<R> supported(
            Collection<R> request, Function<R, String> name, String... names) {
        return request.stream().filter(value -> List.of(names).stream()
                .anyMatch(candidate -> candidate.equalsIgnoreCase(name.apply(value)))).toList();
    }

    private static final class Fixture implements ConnectorContext {
        private final List<String> events = new ArrayList<>();
        private List<String> seenByFallback = List.of();
        private String failAt;
        private ContextLookup activeContext;

        private final OperationExecutor executor = OperationExecutor.transactional(() -> {
            events.add("begin");
            var transaction = new OperationTransaction() {
                @Override
                public <T extends RetrievableContext> T get(Class<T> type) {
                    return Fixture.this.get(type);
                }

                @Override
                public void commit() {
                    events.add("commit");
                }

                @Override
                public void rollback() {
                    events.add("rollback");
                }

                @Override
                public void close() {
                    events.add("close");
                }
            };
            activeContext = transaction;
            return transaction;
        });

        private void step(String name, ContextLookup context) {
            assertSame(context, activeContext);
            events.add(name);
            if (name.equals(failAt)) {
                throw new IllegalStateException("failed " + name);
            }
        }

        private CreateOperationHandler creator(String... names) {
            return new CreateOperationHandler() {
                @Override
                public Capability<Attribute, CreateOperationHandler> canHandle(
                        Collection<Attribute> request, OperationOptions options) {
                    return new Capability<>(this, supported(request, Attribute::getName, names));
                }

                @Override
                public Result create(
                        Set<Attribute> attributes, OperationOptions options, ContextLookup context) {
                    step("create", context);
                    return new Result(ACCOUNT, UID, OBJECT);
                }
            };
        }

        private AttributeCreateOperationHandler child(String name) {
            return new AttributeCreateOperationHandler() {
                @Override
                public Capability<Attribute, AttributeCreateOperationHandler> canHandle(
                        Collection<Attribute> request, OperationOptions options) {
                    return new Capability<>(this, supported(request, Attribute::getName, name));
                }

                @Override
                public void create(Request request, OperationOptions options, ContextLookup context) {
                    assertEquals(request.uid(), UID);
                    step(name, context);
                }
            };
        }

        private UpdateOperationHandler updater(
                String step, boolean original, String... names) {
            return new UpdateOperationHandler() {
                @Override
                public boolean requiresOriginalState() {
                    return original;
                }

                @Override
                public Capability<AttributeDelta, UpdateOperationHandler> canHandle(
                        Collection<AttributeDelta> request, OperationOptions options) {
                    if ("fallback".equals(step)) {
                        seenByFallback = request.stream().map(AttributeDelta::getName).toList();
                    }
                    return new Capability<>(this,
                            supported(request, AttributeDelta::getName, names));
                }

                @Override
                public void update(UpdateOperationBuilder.UpdateRequest request,
                        OperationOptions options, ContextLookup context) {
                    if (original) {
                        assertSame(request.before(), OBJECT);
                    }
                    Fixture.this.step(step, context);
                }
            };
        }

        private DeleteOperationHandler deleter(String step) {
            return new DeleteOperationHandler() {
                @Override
                public void delete(Uid uid, OperationOptions options, ContextLookup context) {
                    assertEquals(uid, UID);
                    Fixture.this.step(step, context);
                }
            };
        }

        @Override
        public ObjectClassHandler handlerFor(ObjectClass objectClass) {
            ObjectSearchOperation search = (context, filter, handler, options) -> {
                step("read", context);
                handler.handle(OBJECT);
            };
            return CompositeObjectClassHandler.of(objectClass, ObjectSearchOperation.class, search);
        }

        @Override
        public BaseSchema schema() {
            return null;
        }

        @Override
        public boolean getDevelopmentMode() {
            return false;
        }

        @Override
        public <T extends RetrievableContext> T getUnchecked(Class<T> type) {
            return type.isInstance(this) ? type.cast(this) : null;
        }
    }
}
