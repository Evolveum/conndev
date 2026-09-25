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

import static org.testng.Assert.*;

/**
 * Verifies that a facade created statically (class-load time, development mode inactive) still
 * resolves its default {@link LogOptions} at write time against the development mode of the
 * writing thread, and that explicitly configured options stay frozen.
 */
public class ConnDevLogOptionsTest {

    /** Created at class-load time, when development mode is not active on this thread. */
    private static final ConnDevLog STATIC_LOG = ConnDevLog.of(ConnDevLogOptionsTest.class);

    private static final String SENSITIVE_BODY = "{\"name\":\"alice\",\"password\":\"s3cret\"}";

    /** Mask value used by {@link DevelopmentModeLogWriter}. */
    private static final String MASK = "****";

    @Test
    public void staticFacadeResolvesDefaultOptionsAtWriteTime() {
        DevelopmentMode.run(false, () -> {
            assertEquals(connIdLog().options(), LogOptions.production());
            return null;
        });
        DevelopmentMode.run(true, () -> {
            assertEquals(connIdLog().options(), LogOptions.development());
            return null;
        });
    }

    private static ConnIdConnDevLog connIdLog() {
        return (ConnIdConnDevLog) STATIC_LOG;
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
        var fixed = ConnDevLog.of(ConnDevLogOptionsTest.class, LogOptions.production());

        DevelopmentMode.run(true, () -> {
            CapturingLogSpi.clear();
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
        CapturingLogSpi.clear();
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
        var traces = OperationLogParser.parse(CapturingLogSpi.messages());
        assertEquals(traces.size(), 1);
        return traces.getFirst();
    }
}
