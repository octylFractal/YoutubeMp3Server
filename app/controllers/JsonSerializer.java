package controllers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class JsonSerializer {

    private static final ObjectMapper JSON = new ObjectMapper();

    public static JsonNode serialize(Object value) {
        return JSON.valueToTree(value);
    }

}
