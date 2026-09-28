package com.example.agent.util;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

public final class Json {

    private Json() {
    }

    /** ObjectMapper shared by the HTTP layer and the SQS publisher. */
    public static ObjectMapper newMapper() {
        return new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }
}
