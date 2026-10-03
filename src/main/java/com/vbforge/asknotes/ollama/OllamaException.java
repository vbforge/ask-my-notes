package com.vbforge.asknotes.ollama;

/**
 * **Why:** it separates the three ways a call can fail, because the API should treat them differently.
 * The exception handler will map `UNAVAILABLE` to 503, `TIMEOUT` to 504 and `BAD_RESPONSE` to 502.
 * This also covers the "clean failure when Ollama is down" requirement from Stage 4 early, essentially for free.
 * */

public class OllamaException extends RuntimeException{

    public enum Kind {
        /** Ollama is not reachable (container down, wrong URL). */
        UNAVAILABLE,
        /** Ollama accepted the request but did not answer within read-timeout. */
        TIMEOUT,
        /** Ollama answered, but with an error, an empty body, or a truncated result. */
        BAD_RESPONSE
    }

    private final Kind kind;

    public OllamaException(Kind kind, String message) {
        super(message);
        this.kind = kind;
    }

    public OllamaException(Kind kind, String message, Throwable cause) {
        super(message, cause);
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }

}
