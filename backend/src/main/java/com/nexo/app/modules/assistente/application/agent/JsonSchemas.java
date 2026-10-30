package com.nexo.app.modules.assistente.application.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Collection;
import java.util.List;

/** Construcao enxuta de JSON Schema para as definicoes de ferramentas. */
public final class JsonSchemas {

    private static final JsonNodeFactory F = JsonNodeFactory.instance;

    private JsonSchemas() {
    }

    public static ObjectBuilder object() {
        return new ObjectBuilder();
    }

    public static ObjectNode string(String description) {
        ObjectNode node = F.objectNode().put("type", "string");
        if (description != null) node.put("description", description);
        return node;
    }

    public static ObjectNode enumString(String description, Collection<String> values) {
        ObjectNode node = string(description);
        ArrayNode array = node.putArray("enum");
        values.forEach(array::add);
        return node;
    }

    public static ObjectNode integer(String description, Integer minimum, Integer maximum) {
        ObjectNode node = F.objectNode().put("type", "integer");
        if (description != null) node.put("description", description);
        if (minimum != null) node.put("minimum", minimum);
        if (maximum != null) node.put("maximum", maximum);
        return node;
    }

    public static ObjectNode number(String description) {
        ObjectNode node = F.objectNode().put("type", "number");
        if (description != null) node.put("description", description);
        return node;
    }

    public static ObjectNode bool(String description) {
        ObjectNode node = F.objectNode().put("type", "boolean");
        if (description != null) node.put("description", description);
        return node;
    }

    public static ObjectNode array(String description, JsonNode items) {
        ObjectNode node = F.objectNode().put("type", "array");
        if (description != null) node.put("description", description);
        node.set("items", items);
        return node;
    }

    public static ObjectNode arrayOfStrings(String description) {
        return array(description, F.objectNode().put("type", "string"));
    }

    public static ObjectNode arrayOfEnum(String description, Collection<String> values) {
        return array(description, enumString(null, values));
    }

    public static ObjectNode anyValue(String description) {
        ObjectNode node = F.objectNode();
        ArrayNode types = node.putArray("type");
        List.of("string", "number", "boolean").forEach(types::add);
        if (description != null) node.put("description", description);
        return node;
    }

    public static final class ObjectBuilder {
        private final ObjectNode node = F.objectNode().put("type", "object");
        private final ObjectNode properties = node.putObject("properties");
        private final ArrayNode required = F.arrayNode();

        public ObjectBuilder description(String description) {
            node.put("description", description);
            return this;
        }

        public ObjectBuilder prop(String name, JsonNode schema) {
            properties.set(name, schema);
            return this;
        }

        public ObjectBuilder required(String name, JsonNode schema) {
            properties.set(name, schema);
            required.add(name);
            return this;
        }

        public ObjectBuilder noAdditional() {
            node.put("additionalProperties", false);
            return this;
        }

        public ObjectNode build() {
            if (!required.isEmpty()) node.set("required", required);
            return node;
        }
    }
}
