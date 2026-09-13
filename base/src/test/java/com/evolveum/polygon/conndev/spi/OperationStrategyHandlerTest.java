/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.spi;

import com.evolveum.polygon.conndev.api.ContextLookup;
import com.evolveum.polygon.conndev.build.api.UpdateOperationBuilder;
import com.evolveum.polygon.conndev.concepts.DevelopmentMode;
import com.evolveum.polygon.conndev.concepts.RetrievableContext;
import com.evolveum.polygon.conndev.devtools.log.LogSeverity;
import com.evolveum.polygon.conndev.devtools.log.OperationLogParser;
import com.evolveum.polygon.conndev.devtools.log.OperationTrace;
import com.evolveum.polygon.conndev.groovy.ConnectorContext;
import com.evolveum.polygon.conndev.logging.CapturingLogProvider;
import com.evolveum.polygon.conndev.logging.ConnDevLog;
import com.evolveum.polygon.conndev.logging.protocol.HttpProtocolData;
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
import java.util.concurrent.Callable;
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

    // ========================================================================
    // Structured log emission
    // ========================================================================

    @Test
    public void updateEmitsRoutingOriginalStateAndStepDetails() throws Exception {
        var fixture = new Fixture();
        var name = delta("name", "Alice");
        var title = delta("title", "Engineer");
        var operation = new UpdateOperationStrategyHandler(fixture, ACCOUNT, fixture.executor,
                List.of(fixture.namedUpdater("name", true, "name"),
                        fixture.namedUpdater("fallback", false, "name", "title")));

        var result = runTraced("update", ACCOUNT, "Update account",
                () -> operation.updateDelta(UID, Set.of(name, title), OPTIONS));

        assertEquals(result, Set.of(name, title));
        var trace = singleTrace();
        assertTrue(trace.completed());
        assertEquals(trace.operation(), "update");
        assertEquals(trace.objectClass(), "account");
        assertTrue(trace.outcome().ok());
        trace.details().forEach(detail -> assertEquals(detail.severity(), LogSeverity.DEBUG));
        assertEquals(trace.details().size(), 5);
        assertEquals(trace.details().getFirst().detail(),
                Map.of("routing", Map.of(
                        "NamedUpdateHandler#1", List.of("name"),
                        "NamedUpdateHandler#2", List.of("title"))));
        assertEquals(trace.details().get(1).detail(), Map.of("readOriginal", true));
        assertEquals(trace.details().get(2).detail(), Map.of("originalState", UID.getUidValue()));
        assertEquals(trace.details().get(3).detail(),
                Map.of("executing", Map.of(
                        "handler", "NamedUpdateHandler#1", "attributes", List.of("name"))));
        assertEquals(trace.details().get(4).detail(),
                Map.of("executing", Map.of(
                        "handler", "NamedUpdateHandler#2", "attributes", List.of("title"))));
    }

    @Test
    public void createEmitsRoutingAndStepDetails() throws Exception {
        var fixture = new Fixture();
        var operation = new CreateOperationStrategyHandler(fixture.executor,
                List.of(fixture.namedCreator(Name.NAME)), List.of(fixture.namedChild("phones")));

        var result = runTraced("create", ACCOUNT, "Create account",
                () -> operation.create(Set.of(
                        new Name("alice"), AttributeBuilder.build("phones", "123")), OPTIONS));

        assertSame(result, OBJECT);
        var trace = singleTrace();
        assertTrue(trace.completed());
        assertEquals(trace.operation(), "create");
        assertEquals(trace.objectClass(), "account");
        assertTrue(trace.outcome().ok());
        assertEquals(trace.details().size(), 3);
        assertEquals(trace.details().getFirst().detail(),
                Map.of("routing", Map.of(
                        "NamedCreateHandler", List.of(Name.NAME),
                        "NamedAttributeCreateHandler", List.of("phones"))));
        assertEquals(trace.details().get(1).detail(),
                Map.of("executing", Map.of(
                        "handler", "NamedCreateHandler", "attributes", List.of(Name.NAME))));
        assertEquals(trace.details().get(2).detail(),
                Map.of("executing", Map.of(
                        "handler", "NamedAttributeCreateHandler", "attributes", List.of("phones"))));
    }

    @Test
    public void deleteEmitsCleanupAndPrimaryDetails() throws Exception {
        var fixture = new Fixture();
        var operation = new DeleteOperationStrategyHandler(fixture.executor,
                List.of(fixture.namedDeleter("parent")), List.of(
                        fixture.namedDeleter("child"), fixture.namedDeleter("junction")));

        runTraced("delete", ACCOUNT, "Delete account", () -> {
            operation.delete(UID, OPTIONS);
            return null;
        });

        var trace = singleTrace();
        assertTrue(trace.completed());
        assertEquals(trace.operation(), "delete");
        assertEquals(trace.objectClass(), "account");
        assertTrue(trace.outcome().ok());
        assertEquals(trace.details().size(), 4);
        assertEquals(trace.details().getFirst().detail(),
                Map.of("cleanup", List.of("NamedDeleteHandler#1", "NamedDeleteHandler#2")));
        assertEquals(trace.details().get(1).detail(),
                Map.of("executing", Map.of("handler", "NamedDeleteHandler#1")));
        assertEquals(trace.details().get(2).detail(),
                Map.of("executing", Map.of("handler", "NamedDeleteHandler#2")));
        assertEquals(trace.details().get(3).detail(),
                Map.of("executing", Map.of("handler", "NamedDeleteHandler#3")));
    }

    @Test
    public void subHandlerProtocolEventsAttachToActiveEntry() throws Exception {
        var fixture = new Fixture();
        var operation = new UpdateOperationStrategyHandler(fixture, ACCOUNT, fixture.executor,
                List.of(fixture.protocolEchoUpdater("name", "name")));

        runTraced("update", ACCOUNT, "Update account",
                () -> operation.updateDelta(UID, Set.of(delta("name", "Alice")), OPTIONS));

        var trace = singleTrace();
        assertEquals(trace.protocolEvents().size(), 2);

        var skeleton = trace.protocolEvents().getFirst();
        assertEquals(skeleton.severity(), LogSeverity.DEBUG);
        var request = skeleton.protocol();
        assertEquals(request.type(), "http");
        assertEquals(request.kind(), "request");
        assertEquals(request.method(), "PUT");
        assertEquals(request.uri(), "/api/account/1");
        assertNull(request.body());

        var body = trace.protocolEvents().get(1);
        assertEquals(body.severity(), LogSeverity.TRACE);
        var bodyPayload = body.protocol();
        assertEquals(bodyPayload.type(), "http");
        assertEquals(bodyPayload.kind(), "request-body");
        assertEquals(bodyPayload.uri(), "/api/account/1");
        assertNotNull(bodyPayload.body());
        assertTrue(bodyPayload.body().contains("\"Alice\""));
    }

    @Test
    public void failureEmitsErrorTraceAndStopsLaterSteps() throws Exception {
        var fixture = new Fixture();
        fixture.failAt = "junction";
        var operation = new DeleteOperationStrategyHandler(fixture.executor,
                List.of(fixture.namedDeleter("parent")), List.of(
                        fixture.namedDeleter("child"), fixture.namedDeleter("junction")));

        expectThrows(IllegalStateException.class, () -> runTraced("delete", ACCOUNT, "Delete account",
                () -> {
                    operation.delete(UID, OPTIONS);
                    return null;
                }));

        var trace = singleTrace();
        assertTrue(trace.completed());
        assertEquals(trace.severity(), LogSeverity.ERROR);
        var outcome = trace.outcome();
        assertTrue(!outcome.ok());
        assertEquals(outcome.message(), "failed junction");
        assertTrue(outcome.stacktrace() != null && !outcome.stacktrace().isEmpty());
        var executed = executingHandlers(trace);
        assertEquals(executed, List.of("NamedDeleteHandler#1", "NamedDeleteHandler#2"));
    }

    @Test
    public void noActiveEntryEmitsNoLines() {
        var fixture = new Fixture();
        var operation = new UpdateOperationStrategyHandler(fixture, ACCOUNT, fixture.executor,
                List.of(fixture.namedUpdater("name", false, "name")));

        CapturingLogProvider.clear();
        DevelopmentMode.run(true, () -> {
            operation.updateDelta(UID, Set.of(delta("name", "Alice")), OPTIONS);
            return null;
        });

        assertTrue(CapturingLogProvider.lines().isEmpty());
    }

    @Test
    public void nonDevModeEmitsPlainLinesAndDropsProtocolEvents() throws Exception {
        var fixture = new Fixture();
        var operation = new UpdateOperationStrategyHandler(fixture, ACCOUNT, fixture.executor,
                List.of(fixture.protocolEchoUpdater("name", "name")));

        runPlain("update", ACCOUNT, "Update account",
                () -> operation.updateDelta(UID, Set.of(delta("name", "Alice")), OPTIONS));

        var lines = CapturingLogProvider.lines();
        assertTrue(lines.stream().noneMatch(OperationLogParser::isStructuredLine));
        assertTrue(lines.stream().anyMatch(line -> line.startsWith("update on account: Update account")));
        assertTrue(lines.stream().anyMatch(line -> line.contains("completed:")));
        assertTrue(lines.stream().anyMatch(line -> line.startsWith("detail routing=")));
        assertTrue(lines.stream().noneMatch(line -> line.contains("PUT")));
    }

    private static AttributeDelta delta(String name, String value) {
        return new AttributeDeltaBuilder().setName(name).addValueToReplace(value).build();
    }

    /** Runs the work inside a dev-mode operation entry, as {@code ClassHandlerConnectorBase} does. */
    private static <V> V runTraced(String operation, ObjectClass objectClass, String message,
            Callable<V> work) throws Exception {
        CapturingLogProvider.clear();
        var log = ConnDevLog.of(OperationStrategyHandlerTest.class);
        return DevelopmentMode.run(true,
                () -> log.runOperation(operation, objectClass, message, () -> work.call()));
    }

    /** Runs the work inside a plain (non dev-mode) operation entry. */
    private static <V> V runPlain(String operation, ObjectClass objectClass, String message,
            Callable<V> work) throws Exception {
        CapturingLogProvider.clear();
        var log = ConnDevLog.of(OperationStrategyHandlerTest.class);
        return DevelopmentMode.run(false,
                () -> log.runOperation(operation, objectClass, message, () -> work.call()));
    }

    /** Parses the captured lines and asserts exactly one operation entry was reconstructed. */
    private static OperationTrace singleTrace() {
        var traces = OperationLogParser.parse(CapturingLogProvider.lines());
        assertEquals(traces.size(), 1);
        return traces.getFirst();
    }

    private static List<String> executingHandlers(OperationTrace trace) {
        var executed = new ArrayList<String>();
        for (var detail : trace.details()) {
            if (detail.detail().get("executing") instanceof Map<?, ?> kvs) {
                executed.add(String.valueOf(kvs.get("handler")));
            }
        }
        return executed;
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

        private CreateOperationHandler namedCreator(String... names) {
            return new NamedCreateHandler(this, names);
        }

        private AttributeCreateOperationHandler namedChild(String name) {
            return new NamedAttributeCreateHandler(this, name, name);
        }

        private UpdateOperationHandler namedUpdater(String step, boolean original, String... names) {
            return new NamedUpdateHandler(this, step, original, names);
        }

        private UpdateOperationHandler protocolEchoUpdater(String step, String... names) {
            return new ProtocolEchoUpdateHandler(this, step, names);
        }

        private DeleteOperationHandler namedDeleter(String step) {
            return new NamedDeleteHandler(this, step);
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

    /** Named handler classes so that {@code OperationTracing} labels are stable class names. */

    private static final class NamedCreateHandler implements CreateOperationHandler {

        private final Fixture fixture;
        private final List<String> names;

        private NamedCreateHandler(Fixture fixture, String... names) {
            this.fixture = fixture;
            this.names = List.of(names);
        }

        @Override
        public Capability<Attribute, CreateOperationHandler> canHandle(
                Collection<Attribute> request, OperationOptions options) {
            return new Capability<>(this, supported(request, Attribute::getName,
                    names.toArray(String[]::new)));
        }

        @Override
        public Result create(Set<Attribute> attributes, OperationOptions options,
                ContextLookup context) {
            fixture.step("create", context);
            return new Result(ACCOUNT, UID, OBJECT);
        }
    }

    private static final class NamedAttributeCreateHandler implements AttributeCreateOperationHandler {

        private final Fixture fixture;
        private final String step;
        private final List<String> names;

        private NamedAttributeCreateHandler(Fixture fixture, String step, String... names) {
            this.fixture = fixture;
            this.step = step;
            this.names = List.of(names);
        }

        @Override
        public Capability<Attribute, AttributeCreateOperationHandler> canHandle(
                Collection<Attribute> request, OperationOptions options) {
            return new Capability<>(this, supported(request, Attribute::getName,
                    names.toArray(String[]::new)));
        }

        @Override
        public void create(Request request, OperationOptions options, ContextLookup context) {
            assertEquals(request.uid(), UID);
            fixture.step(step, context);
        }
    }

    private static final class NamedUpdateHandler implements UpdateOperationHandler {

        private final Fixture fixture;
        private final String step;
        private final boolean original;
        private final List<String> names;

        private NamedUpdateHandler(Fixture fixture, String step, boolean original, String... names) {
            this.fixture = fixture;
            this.step = step;
            this.original = original;
            this.names = List.of(names);
        }

        @Override
        public boolean requiresOriginalState() {
            return original;
        }

        @Override
        public Capability<AttributeDelta, UpdateOperationHandler> canHandle(
                Collection<AttributeDelta> request, OperationOptions options) {
            return new Capability<>(this, supported(request, AttributeDelta::getName,
                    names.toArray(String[]::new)));
        }

        @Override
        public void update(UpdateOperationBuilder.UpdateRequest request,
                OperationOptions options, ContextLookup context) {
            if (original) {
                assertSame(request.before(), OBJECT);
            }
            fixture.step(step, context);
        }
    }

    /** Behaves like {@link NamedUpdateHandler} but also attaches an HTTP protocol event. */
    private static final class ProtocolEchoUpdateHandler implements UpdateOperationHandler {

        private final Fixture fixture;
        private final String step;
        private final List<String> names;

        private ProtocolEchoUpdateHandler(Fixture fixture, String step, String... names) {
            this.fixture = fixture;
            this.step = step;
            this.names = List.of(names);
        }

        @Override
        public boolean requiresOriginalState() {
            return false;
        }

        @Override
        public Capability<AttributeDelta, UpdateOperationHandler> canHandle(
                Collection<AttributeDelta> request, OperationOptions options) {
            return new Capability<>(this, supported(request, AttributeDelta::getName,
                    names.toArray(String[]::new)));
        }

        @Override
        public void update(UpdateOperationBuilder.UpdateRequest request,
                OperationOptions options, ContextLookup context) {
            fixture.step(step, context);
            var entry = ConnDevLog.of(getClass()).currentOperation();
            if (entry != null) {
                entry.http(new HttpProtocolData.Request("PUT", "/api/account/1",
                        Map.of("name", "Alice", "password", "s3cret")));
            }
        }
    }

    private static final class NamedDeleteHandler implements DeleteOperationHandler {

        private final Fixture fixture;
        private final String step;

        private NamedDeleteHandler(Fixture fixture, String step) {
            this.fixture = fixture;
            this.step = step;
        }

        @Override
        public void delete(Uid uid, OperationOptions options, ContextLookup context) {
            assertEquals(uid, UID);
            fixture.step(step, context);
        }
    }
}
