package com.vbforge.asknotes.api;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Locale;

/**
 * Why an enum: it makes the allowed values a compile-time fact instead of a loose string.
 * @JsonValue and @JsonCreator let the JSON use lowercase "high" (matching our Ollama schema and the page's CSS classes)
 * while Java uses HIGH.
 * If the model ever returns something outside the three values, parsing fails loudly and we can turn that into a clean error.
 * Jackson 3 kept these annotations in their old com.fasterxml.jackson.annotation package, so only the databind classes moved to tools.jackson.
 * */

public enum Confidence {

    LOW, MEDIUM, HIGH;

    @JsonValue
    public String value() {
        return name().toLowerCase(Locale.ROOT);
    }

    @JsonCreator
    public static Confidence fromValue(String raw) {
        return valueOf(raw.trim().toUpperCase(Locale.ROOT));
    }

}
