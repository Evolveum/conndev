/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.concepts;

import groovy.lang.Closure;
import org.testng.annotations.Test;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

/**
 * Tests for GroovyClosures utility class, using actual Groovy closure strings
 * (written as code, evaluated at runtime by GroovyShell) rather than Java anonymous classes.
 * 
 * Each test passes a Groovy code string to {@code groovyShellEval(String)} which wraps it
 * as {@code "return { ... }"} so GroovyShell produces an actual Closure instance with 
 * a proper delegate context.
 */
public class GroovyClosuresTest {

    /**
     * Evaluates Groovy code as a closure, wrapping the input in {@code return { ... }}
     * so GroovyShell produces a real Closure (not just a Script).
     */
    @SuppressWarnings({"GroovyAssignabilityCheck", "unchecked"})
    private static <T> Closure<T> groovyShellEval(String groovyCode) {
        return (Closure<T>) new groovy.lang.GroovyShell().evaluate("return { " + groovyCode + " }");
    }

    @Test
    public void callAndReturnDelegate_mutatesDelegate() {
        var delegate = new ConfigDelegate();
        // Groovy closure string: delegates to Java object via DELEGATE_FIRST
        Closure<?> closure = groovyShellEval("name = 'test'");

        ConfigDelegate ret = GroovyClosures.callAndReturnDelegate(closure, delegate);

        assertSame(ret, delegate);
        assertEquals(delegate.name, "test");
    }

    @Test
    public void callAndReturnDelegate_returnsDelegateNotClosureReturnValue() {
        var delegate = new ConfigDelegate();
        Closure<?> closure = groovyShellEval("name = 'set'; return 'ignored'");

        ConfigDelegate ret = GroovyClosures.callAndReturnDelegate(closure, delegate);

        assertSame(ret, delegate);
        assertEquals(delegate.name, "set");
    }

    @Test
    public void callAndReturnDelegate_multipleMutations() {
        var delegate = new ConfigDelegate();
        Closure<?> closure = groovyShellEval("name = 'a'; value = 42");

        GroovyClosures.callAndReturnDelegate(closure, delegate);

        assertEquals(delegate.name, "a");
        assertEquals(delegate.value, 42);
    }

    @Test
    public void callAndReturnDelegate_closureCanReturnNull() {
        var delegate = new ConfigDelegate();
        Closure<?> closure = groovyShellEval("name = 'x'; return null");

        ConfigDelegate ret = GroovyClosures.callAndReturnDelegate(closure, delegate);

        assertSame(ret, delegate);
        assertEquals(delegate.name, "x");
    }

    @Test
    public void callAndReturnDelegate_closureAccessLocalVars() {
        var delegate = new ConfigDelegate();
        // Verify Groovy closure string works with multi-line code
        Closure<?> closure = groovyShellEval("""
                name = 'multi'
                value = 100
                return null
            """
        );

        GroovyClosures.callAndReturnDelegate(closure, delegate);

        assertEquals(delegate.name, "multi");
        assertEquals(delegate.value, 100);
    }

    @Test
    public void copyAndCall_doesNotMutatePrototype() {
        Closure<String> prototype = groovyShellEval("value = 1; return 'done'");

        String firstResult = GroovyClosures.copyAndCall(prototype, new RuntimeDelegate(0));

        var secondDelegate = new RuntimeDelegate(1);
        String secondResult = GroovyClosures.copyAndCall(prototype, secondDelegate);

        assertEquals(firstResult, "done");
        assertEquals(secondResult, "done");
        // Prototype still works after multiple calls (it's cloned each time internally)
        String thirdResult = GroovyClosures.copyAndCall(prototype, new RuntimeDelegate(2));
        assertEquals(thirdResult, "done");
    }

    @Test
    public void copyAndCall_returnsClosureResult() {
        Closure<String> prototype = groovyShellEval("name = 'called'; return 'result'");

        var delegate = new RuntimeDelegate(-1);
        String result = GroovyClosures.copyAndCall(prototype, delegate);

        assertEquals(result, "result");
        assertEquals(delegate.name, "called");
    }

    @Test
    public void copyAndCall_returnsNull() {
        Closure<String> prototype = groovyShellEval("return null");

        var delegate = new RuntimeDelegate(0);
        String result = GroovyClosures.copyAndCall(prototype, delegate);

        assertNull(result);
    }

    @Test
    public void copyAndCall_accessDelegateValue() {
        Closure<Integer> prototype = groovyShellEval("return base * 2");

        var delegate = new RuntimeDelegate(10);
        int result = GroovyClosures.copyAndCall(prototype, delegate);

        assertEquals(result, 20);
    }

    @Test
    public void copyAndCall_closureWithReturnAndAssignment() {
        Closure<String> prototype = groovyShellEval(
            """
            counter = (delegate.counter ?: 0) + 1;
            return 'base=' + delegate.base"""
        );

        var delegate = new RuntimeDelegate(5);
        delegate.counter = 0;

        String result = GroovyClosures.copyAndCall(prototype, delegate);

        assertEquals(result, "base=5");
        assertEquals(delegate.counter, 1);
    }

    @Test
    public void copyAndCall_closureImplicitReturn() {
        Closure<String> prototype = groovyShellEval("value = 42; 'final'");

        var delegate = new RuntimeDelegate(0);
        String result = GroovyClosures.copyAndCall(prototype, delegate);

        assertEquals(result, "final");
        assertEquals(delegate.value, 42);
    }

    @Test
    public void copyAndCall_closureArithmetic() {
        Closure<Integer> prototype = groovyShellEval("return delegate.base + delegate.value");

        var delegate = new RuntimeDelegate(10);
        delegate.value = 5;

        int result = GroovyClosures.copyAndCall(prototype, delegate);

        assertEquals(result, 15);
    }

    @Test
    public void copyAndCall_closureNumericReturn() {
        Closure<Integer> prototype = groovyShellEval("return delegate.base * 3 + 5");

        var delegate = new RuntimeDelegate(7);
        int result = GroovyClosures.copyAndCall(prototype, delegate);

        assertEquals(result, 26);
    }

    @Test
    public void copyAndCall_closureStringConcat() {
        Closure<String> prototype = groovyShellEval("return 'Hello, ' + delegate.name + '!'");

        var delegate = new RuntimeDelegate(0);
        delegate.name = "World";

        String result = GroovyClosures.copyAndCall(prototype, delegate);

        assertEquals(result, "Hello, World!");
    }

    @Test
    public void closureExecutionAware_beforeExecution() {
        var beforeCalled = new AtomicBoolean(false);
        AtomicReference<String> capturedValue = new AtomicReference<>();

        Closure<?> closure = groovyShellEval("name = 'before-closure'");

        var delegate = new TestExecutionAware();
        delegate.setBefore(() -> {
            beforeCalled.set(true);
            capturedValue.set((String) delegate.name);
        });

        GroovyClosures.callAndReturnDelegate(closure, delegate);

        assertTrue(beforeCalled.get());
        assertNull(capturedValue.get());
        assertEquals(delegate.name, "before-closure");
    }

    @Test
    public void closureExecutionAware_afterExecution() {
        Closure<?> closure = groovyShellEval("name = 'after-check'");

        var afterCalled = new AtomicBoolean(false);
        AtomicReference<String> capturedValue = new AtomicReference<>();

        var delegate = new TestExecutionAware();
        delegate.setAfter(() -> {
            afterCalled.set(true);
            capturedValue.set((String) delegate.name);
        });

        GroovyClosures.callAndReturnDelegate(closure, delegate);

        assertTrue(afterCalled.get());
        assertEquals(capturedValue.get(), "after-check");
    }

    @Test
    public void closureExecutionAware_beforeAfterChain() {
        Closure<?> closure = groovyShellEval("value = 99");

        var calls = new AtomicInteger(0);

        var delegate = new TestExecutionAware();
        delegate.setBefore(calls::getAndIncrement);
        delegate.setAfter(calls::incrementAndGet);

        GroovyClosures.callAndReturnDelegate(closure, delegate);

        assertEquals(calls.get(), 2);
    }

    @Test
    public void closureExecutionAware_onException() {
        var beforeCalled = new AtomicBoolean(false);
        var afterCalled = new AtomicBoolean(false);

        Closure<?> closure = groovyShellEval("throw new IllegalArgumentException('boom')");

        var delegate = new TestExecutionAware();
        delegate.setBefore(() -> beforeCalled.set(true));
        delegate.setAfter(() -> afterCalled.set(true));

        try {
            GroovyClosures.callAndReturnDelegate(closure, delegate);
            fail("Should have thrown");
        } catch (IllegalArgumentException e) {
            assertEquals(e.getMessage(), "boom");
            assertTrue(beforeCalled.get());
            // afterExecution is NOT called on exception path in current implementation
            assertFalse(afterCalled.get());
        }
    }

    @Test
    public void copyAndCall_closureThrowsException() {
        Closure<String> prototype = groovyShellEval("throw new IllegalArgumentException('boom')");

        try {
            GroovyClosures.copyAndCall(prototype, new RuntimeDelegate(0));
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertEquals(e.getMessage(), "boom");
        }
    }

    @Test
    public void copyAndCall_closureNoOp() {
        Closure<String> prototype = groovyShellEval("return 'no-op'");

        var delegate = new RuntimeDelegate(0);
        String result = GroovyClosures.copyAndCall(prototype, delegate);

        assertEquals(result, "no-op");
    }

    @Test
    public void asFunction_concatenation() {
        Closure<String> closure = groovyShellEval("return it + ' world'");

        Function<String, String> fn = GroovyClosures.asFunction(closure);

        assertEquals(fn.apply("hello"), "hello world");
    }


    @Test
    public void asFunction_multipleCallsIndependent() {
        Closure<Integer> closure = groovyShellEval("return it * 3");

        Function<Integer, Integer> fn = GroovyClosures.asFunction(closure);

        int r1 = fn.apply(5);
        int r2 = fn.apply(4);

        assertEquals(r1, 15);
        assertEquals(r2, 12);
    }

    @Test
    public void asFunction_closureWithLocalClosure() {
        Closure<String> closure = groovyShellEval("return it.toUpperCase()");

        Function<String, String> fn = GroovyClosures.asFunction(closure);

        assertEquals(fn.apply("mixedCase"), "MIXEDCASE");
    }

    private static class ConfigDelegate {
        String name;
        int value;
    }

    private static class RuntimeDelegate {
        String name;
        int value;
        int counter;
        final int base;

        RuntimeDelegate(int base) {
            this.base = base;
        }
    }

    private static class TestExecutionAware implements GroovyClosures.ClosureExecutionAware {
        Object name;
        Object value;
        Runnable before;
        Runnable after;

        void setBefore(Runnable before) { this.before = before; }

        void setAfter(Runnable after) { this.after = after; }

        @Override
        public void beforeExecution() {
            if (before != null) before.run();
        }

        @Override
        public void afterExecution() {
            if (after != null) after.run();
        }
    }
}