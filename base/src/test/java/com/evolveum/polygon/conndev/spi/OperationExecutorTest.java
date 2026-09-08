/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.spi;

import com.evolveum.polygon.conndev.api.ContextLookup;
import com.evolveum.polygon.conndev.concepts.RetrievableContext;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

public class OperationExecutorTest {

    @Test
    public void successCommitsAndCloses() {
        var transaction = new TestTransaction(1);
        var result = OperationExecutor.transactional(() -> transaction)
                .execute(context -> context.get(Resource.class).id());

        assertEquals(result, 1);
        assertEquals(transaction.events, List.of("commit", "close"));
    }

    @Test
    public void workFailureRollsBackAndCloses() {
        var transaction = new TestTransaction(1);
        var failure = new IllegalStateException("failed");

        var actual = expectThrows(IllegalStateException.class,
                () -> OperationExecutor.transactional(() -> transaction).execute(context -> {
                    throw failure;
                }));

        assertSame(actual, failure);
        assertEquals(transaction.events, List.of("rollback", "close"));
    }

    @Test
    public void commitFailureAttemptsRollbackAndKeepsCleanupFailures() {
        var transaction = new TestTransaction(1);
        transaction.commitFailure = new Exception("commit");
        transaction.rollbackFailure = new Exception("rollback");

        var failure = expectThrows(ConnectorException.class,
                () -> OperationExecutor.transactional(() -> transaction).execute(context -> "done"));

        assertSame(failure.getCause(), transaction.commitFailure);
        assertEquals(transaction.commitFailure.getSuppressed(), new Throwable[]{ transaction.rollbackFailure });
        assertEquals(transaction.events, List.of("commit", "rollback", "close"));
    }

    @Test
    public void closeFailureAfterCommitDoesNotRollback() {
        var transaction = new TestTransaction(1);
        transaction.closeFailure = new Exception("close");

        expectThrows(ConnectorException.class,
                () -> OperationExecutor.transactional(() -> transaction).execute(context -> "done"));

        assertEquals(transaction.events, List.of("commit", "close"));
    }

    @Test
    public void openFailureDoesNotRunWork() {
        var ran = new AtomicBoolean();
        expectThrows(ConnectorException.class,
                () -> OperationExecutor.transactional(() -> {
                    throw new Exception("open");
                }).execute(context -> {
                    ran.set(true);
                    return null;
                }));
        assertTrue(!ran.get());
    }

    @Test
    public void directExecutionUsesExistingContext() {
        var resource = new Resource(7);
        ContextLookup context = new ContextLookup() {
            @Override
            public <T extends RetrievableContext> T get(Class<T> type) {
                return type.cast(resource);
            }
        };

        int result = OperationExecutor.direct(context)
                .execute(actual -> actual.get(Resource.class).id());
        assertEquals(result, 7);
    }

    private record Resource(int id) implements RetrievableContext {
    }

    private static final class TestTransaction implements OperationTransaction {
        private final List<String> events = new ArrayList<>();
        private final Resource resource;
        private Exception commitFailure;
        private Exception rollbackFailure;
        private Exception closeFailure;

        private TestTransaction(int id) {
            resource = new Resource(id);
        }

        @Override
        public <T extends RetrievableContext> T get(Class<T> type) {
            return type.cast(resource);
        }

        @Override
        public void commit() throws Exception {
            events.add("commit");
            if (commitFailure != null) throw commitFailure;
        }

        @Override
        public void rollback() throws Exception {
            events.add("rollback");
            if (rollbackFailure != null) throw rollbackFailure;
        }

        @Override
        public void close() throws Exception {
            events.add("close");
            if (closeFailure != null) throw closeFailure;
        }
    }
}
