package com.vbforge.asknotes.ollama;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/** Wire format of Ollama's POST /api/embed. */
public final class OllamaEmbed {

    private OllamaEmbed() {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Request(
            String model,
            List<String> input,     // /api/embed accepts one string or a list; we always send a list
            Boolean truncate
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Response(
            List<float[]> embeddings    // one vector per input, same order
    ) {
    }
}