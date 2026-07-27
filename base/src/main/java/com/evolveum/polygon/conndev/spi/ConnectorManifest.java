package com.evolveum.polygon.conndev.spi;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;
import org.identityconnectors.framework.common.exceptions.ConfigurationException;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class ConnectorManifest {
    private final JsonNode json;

    public ConnectorManifest(InputStream resource) {
        try {
            if (resource == null) {
                json = JsonNodeFactory.instance.objectNode();
            } else {
                json = new ObjectMapper().readTree(resource);
            }
        } catch (JacksonException e) {
            throw new ConfigurationException("Failed to read connector manifest", e);
        } finally {
            //resource.close();
        }
    }

    ObjectNode connector() {
        return require(ObjectNode.class, json.get("connector"), "connector object not present");
    }

    List<String> scripts(String type) {

        var schemaScripts = connector().get(type);
        if (schemaScripts == null || schemaScripts.isEmpty()) {
            return new ArrayList<>();
        }
        var ret = new ArrayList<String>(schemaScripts.size());
        for  (JsonNode schema : schemaScripts) {
            ret.add(schema.get("script").asText());
        }
        return ret;
    }

    public List<String> schemaScripts() {
        return scripts("schema");
    }

    public List<String> authorizationScripts() {
        return scripts("authorization");
    }

    public List<String> operationScripts() {
        return scripts("operation");
    }


    private <T extends JsonNode> T require(Class<T> clazz, JsonNode node, String message) {
        if (node == null || !clazz.isInstance(node)) {
            throw new IllegalArgumentException(message);
        }
        return clazz.cast(node);
    }
}
