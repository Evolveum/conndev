/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.devtools.log;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;

/**
 * Deserializes the {@code location} field of a {@link StructuredLogEvent}.
 *
 * <p>The current wire format carries the location as a plain string. Lines written by older
 * emitters carry a location object with {@code className}, {@code methodName}, {@code file}
 * and {@code line} fields; those are converted to the string form so that historical log lines
 * keep parsing:
 *
 * <ul>
 *   <li>{@code {"className":...,"methodName":"m","file":"F.java","line":42}} → {@code m(F.java:42)}</li>
 *   <li>{@code {"file":"S.groovy","line":42}} → {@code S.groovy}</li>
 * </ul>
 */
public class LocationDeserializer extends JsonDeserializer<String> {

    @Override
    public String deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        if (parser.currentToken() == JsonToken.VALUE_STRING) {
            return parser.getText();
        }
        if (parser.currentToken() != JsonToken.START_OBJECT) {
            return null;
        }
        var node = (JsonNode) parser.getCodec().readTree(parser);
        var className = node.path("className").asText(null);
        var method = node.path("methodName").asText(null);
        var file = node.path("file").asText(null);
        var line = node.path("line").asInt(0);
        if (method != null && !method.isEmpty()) {
            var source = file != null && line > 0 ? file + ":" + line : className;
            return source != null ? method + "(" + source + ")" : method;
        }
        return file != null ? file : className;
    }
}
