/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.spi;

import com.evolveum.polygon.conndev.api.ContextLookup;
import com.evolveum.polygon.conndev.concepts.CheckedCallable;
import com.evolveum.polygon.conndev.concepts.DevelopmentMode;
import com.evolveum.polygon.conndev.groovy.*;
import com.evolveum.polygon.conndev.logging.ConnDevLog;
import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.identityconnectors.framework.common.objects.*;
import org.identityconnectors.framework.common.objects.filter.Filter;
import org.identityconnectors.framework.common.objects.filter.FilterTranslator;
import org.identityconnectors.framework.spi.Connector;
import org.identityconnectors.framework.spi.operations.*;

import java.util.List;
import java.util.Set;

/**
 * Generic Base Connector with support for separate handlers per Object Class
 *
 * This allows for example to mix-and-match SCIM handlers for users, with Scripted Handlers
 * for groups or repositories (eg. Github, which allows only user provisioning via SCIM).
 * or pure REST connector with customized handlers per object class (eg. Forgejo).
 *
 * The retrieval of correct Object Class handler should be done in {@link #handlerFor(ObjectClass)} method.
 *
 * Handlers needs to implement {@link ObjectClassHandler} interface.
 *
 */
public abstract class ClassHandlerConnectorBase implements Connector,
        AuthenticateOp, CreateOp, DeleteOp, ResolveUsernameOp,
        SchemaOp, SearchOp<Filter>, TestOp,
        UpdateDeltaOp, SyncOp, ScriptOnResourceOp {

    public abstract ContextLookup context();

    public abstract ObjectClassHandler handlerFor(ObjectClass objectClass) throws UnsupportedOperationException;

    /**
     * Logging facade bound to this connector's class, created once per connector instance so
     * that each connector (including Groovy script subclasses) logs under its own class.
     * Every ConnId operation dispatched through this class is wrapped in an operation entry
     * logged via this facade.
     */
    private final ConnDevLog log = ConnDevLog.of(getClass());

    /**
     * Returns the logging facade bound to this connector.
     *
     * @return the logging facade
     */
    protected ConnDevLog log() {
        return log;
    }

    /**
     * Executes the work within a new operation entry with development mode active for the
     * duration of the operation when the {@link ConnectorContext} configuration enables it,
     * so that the entry's events are emitted as structured log lines.
     *
     * @param operation   the ConnId operation name
     * @param objectClass the object class the operation applies to
     * @param message     human-readable description of the operation
     * @param work        the work to execute
     * @param <V>         the result type
     * @param <E>         the exception type the work may throw
     * @return the result of the work
     * @throws E if the work fails
     */
    private <V, E extends Throwable> V runTraced(String operation, ObjectClass objectClass, String message,
            CheckedCallable<V, E> work) throws E {
        return DevelopmentMode.run(developmentMode(),
                () -> log().runOperation(operation, objectClass, message, work));
    }

    private boolean developmentMode() {
        return context() instanceof ConnectorContext connectorContext
                && connectorContext.getDevelopmentMode();
    }

    @Override
    public Uid authenticate(ObjectClass objectClass, String username, GuardedString password, OperationOptions options) {
        throw new UnsupportedOperationException("Not supported yet.");
        //return handlerFor(objectClass).checkSupported(AuthenticateOp.class).authenticate(username, password, options);
    }

    @Override
    public Uid create(ObjectClass objectClass, Set<Attribute> createAttributes, OperationOptions options) {
        return runTraced("create", objectClass, "Create " + objectClass.getObjectClassValue(), () ->
                handlerFor(objectClass).checkSupported(ObjectCreateOperation.class).create(createAttributes, options).getUid());
    }

    @Override
    public void delete(ObjectClass objectClass, Uid uid, OperationOptions options) {
        runTraced("delete", objectClass, "Delete " + objectClass.getObjectClassValue(), () -> {
            handlerFor(objectClass).checkSupported(ObjectDeleteOperation.class).delete(uid, options);
            return null;
        });
    }

    @Override
    public Uid resolveUsername(ObjectClass objectClass, String username, OperationOptions options) {
        throw new UnsupportedOperationException("Not supported yet.");
        //return handlerFor(objectClass).checkSupported(ResolveUsernameOp.class).resolveUsername(username, options);
    }

    @Override
    public FilterTranslator<Filter> createFilterTranslator(ObjectClass objectClass, OperationOptions options) {

        return f -> {
            if (f == null) {
                return List.of();
            }
            return List.of(f);
        };
    }

    @Override
    public void executeQuery(ObjectClass objectClass, Filter query, ResultsHandler handler, OperationOptions options) {
        try {
            runTraced("search", objectClass, "Search " + objectClass.getObjectClassValue(), () -> {
                handlerFor(objectClass)
                        .checkSupported(ObjectSearchOperation.class)
                        .executeQuery(context(), query, handler, options);
                return null;
            });
        } catch (ConnectorException e) {
            throw e;
        } catch (Exception e) {
            throw new ConnectorException(e);
        }
    }

    @Override
    public Set<AttributeDelta> updateDelta(ObjectClass objclass, Uid uid, Set<AttributeDelta> modifications, OperationOptions options) {
        return runTraced("update", objclass, "Update " + objclass.getObjectClassValue(), () ->
                handlerFor(objclass).checkSupported(ObjectUpdateOperation.class).updateDelta(uid, modifications, options));
    }

    @Override
    public void sync(ObjectClass objectClass, SyncToken token,
                     SyncResultsHandler handler, OperationOptions options) {
        runTraced("sync", objectClass, "Sync " + objectClass.getObjectClassValue(), () -> {
            handlerFor(objectClass).checkSupported(ObjectSyncOperation.class)
                    .sync(token, handler, options, context());
            return null;
        });
    }

    @Override
    public SyncToken getLatestSyncToken(ObjectClass objectClass) {
        return runTraced("syncToken", objectClass, "Get latest sync token", () ->
                handlerFor(objectClass).checkSupported(ObjectSyncOperation.class)
                        .getLatestSyncToken());
    }

    @Override
    public Object runScriptOnResource(ScriptContext request, OperationOptions options) {
        if (!(getConfiguration() instanceof BaseGroovyConnectorConfiguration groovyConf)
                || !Boolean.TRUE.equals(groovyConf.getDevelopmentMode())) {
            throw new UnsupportedOperationException("Script execution is supported only in development mode");
        }
        if (!ScriptValidationRequest.LANGUAGE_GROOVY.equalsIgnoreCase(request.getScriptLanguage())
                && !ScriptValidationRequest.LANGUAGE_YAML.equalsIgnoreCase(request.getScriptLanguage())) {
            throw new IllegalArgumentException("Unsupported script language: " + request.getScriptLanguage());
        }
        var validationRequest = ScriptValidationRequest.from(request);
        if (!ScriptValidationRequest.SCRIPT_OPERATION_BUILD.equals(validationRequest.operation())
                && !ScriptValidationRequest.SCRIPT_OPERATION_COMPILE.equals(validationRequest.operation())) {
            throw new UnsupportedOperationException(
                    "Unsupported script operation, only '" + ScriptValidationRequest.SCRIPT_OPERATION_BUILD + "' or '"
                            + ScriptValidationRequest.SCRIPT_OPERATION_COMPILE + "' is supported");
        }
        try {
            return validateScript(validationRequest).toMap();
        } catch (Exception e) {
            return GroovyScriptValidator.error(ScriptError.Phase.INITIALIZATION, e).toMap();
        }
    }

    /**
     * Validates the candidate script described by {@code request} — via {@link
     * GroovyScriptValidator} for Groovy, or the YAML counterpart for YAML ({@link
     * ScriptValidationRequest#isYaml()}). {@link ScriptValidationRequest#filename} identifies the
     * already-deployed resource, if any, so implementations can reload every other deployed
     * script while excluding this one. May throw if the connector can't be initialized enough to
     * build a throwaway validation target; reported as an {@code initialization}-phase error.
     */
    protected abstract ScriptValidationResult validateScript(ScriptValidationRequest request) throws Exception;

    /**
     * Resource names of the currently deployed schema scripts, minus {@code excludedResource} —
     * used by {@link #validateScript} implementations to reload siblings while validating a
     * not-yet-saved replacement for one of them. Connectors with no such concept of named
     * resources (e.g. hardcoding their scripts) don't need to override this; validation then
     * simply won't have sibling schema scripts loaded.
     */
    protected List<String> schemaResources(String excludedResource) {
        return List.of();
    }

    /** Same as {@link #schemaResources}, for operation/handler scripts. */
    protected List<String> operationResources(String excludedResource) {
        return List.of();
    }
}
