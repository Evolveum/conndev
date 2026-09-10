/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.logging;

import com.evolveum.polygon.conndev.concepts.DevelopmentMode;
import com.evolveum.polygon.conndev.devtools.log.ConndevLogFormat;
import com.evolveum.polygon.conndev.devtools.log.OperationLogParser;
import com.evolveum.polygon.conndev.devtools.log.OperationTrace;
import com.evolveum.polygon.conndev.logging.protocol.HttpProtocolData;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * Verifies that a facade created statically (class-load time, development mode inactive) still
 * resolves its default {@link LogOptions} at write time against the development mode of the
 * writing thread, and that explicitly configured options stay frozen.
 */
public class Slf4JConnDevLogOptionsTest {

    /** Created at class-load time, when development mode is not active on this thread. */
    private static final ConnDevLog STATIC_LOG = ConnDevLog.of(Slf4JConnDevLogOptionsTest.class);

    private static final String SENSITIVE_BODY = "{\"name\":\"alice\",\"password\":\"s3cret\"}";

    /** Mask value used by {@link DevelopmentModeLogWriter}. */
    private static final String MASK = "****";

    @Test
    public void staticFacadeResolvesDefaultOptionsAtWriteTime() {
        DevelopmentMode.run(false, () -> {
            assertEquals(sl4jLog().options(), LogOptions.production());
            return null;
        });
        DevelopmentMode.run(true, () -> {
            assertEquals(sl4jLog().options(), LogOptions.development());
            return null;
        });
    }

    private static Slf4JConnDevLog sl4jLog() {
        return (Slf4JConnDevLog) STATIC_LOG;
    }

    @Test
    public void staticFacadeLeavesSensitiveValuesUnredactedInDevelopmentMode() {
        DevelopmentMode.run(true, () -> {
            emitSensitiveRequest();
            return null;
        });

        var body = requestBodyBody(singleTrace());
        assertTrue(body.contains("s3cret"));
        assertFalse(body.contains(MASK));
    }

    @Test
    public void explicitOptionsStayFrozenRegardlessOfMode() {
        var fixed = ConnDevLog.of(Slf4JConnDevLogOptionsTest.class, LogOptions.production());

        DevelopmentMode.run(true, () -> {
            CapturingLogProvider.clear();
            fixed.runOperation("update", new ObjectClass("account"), "Update account", () -> {
                fixed.currentOperation()
                        .http(new HttpProtocolData.Request("PUT", "/api/account/1", SENSITIVE_BODY));
                return null;
            });
            return null;
        });

        var body = requestBodyBody(singleTrace());
        assertFalse(body.contains("s3cret"));
        assertTrue(body.contains(MASK));
    }

    private static void emitSensitiveRequest() {
        CapturingLogProvider.clear();
        STATIC_LOG.runOperation("update", new ObjectClass("account"), "Update account", () -> {
            STATIC_LOG.currentOperation()
                    .http(new HttpProtocolData.Request("PUT", "/api/account/1", SENSITIVE_BODY));
            return null;
        });
    }

    private static String requestBodyBody(OperationTrace trace) {
        return trace.protocolEvents().stream()
                .filter(event -> ConndevLogFormat.HTTP_REQUEST_BODY.equals(event.protocol().kind()))
                .map(event -> event.protocol().body())
                .findFirst()
                .orElseThrow();
    }

    private static OperationTrace singleTrace() {
        var traces = OperationLogParser.parse(CapturingLogProvider.lines());
        assertEquals(traces.size(), 1);
        return traces.get(0);
    }
}
