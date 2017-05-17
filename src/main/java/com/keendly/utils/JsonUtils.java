package com.keendly.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.ws.rs.core.Response;
import java.io.IOException;

public class JsonUtils {

    private static ObjectMapper mapper = new ObjectMapper();

    public static JsonNode asJson(Response response) {
        String content = response.readEntity(String.class);
        try {
            return mapper.readTree(content);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
