package com.vbforge.asknotes.api;

import java.util.List;

public record AskResponse(
        String answer,
        Confidence confidence,
        List<Source> sources
) {

    /** One cited note section: file plus heading path. */
    public record Source(String file, String heading) {
    }

    public AskResponse {
        sources = sources == null ? List.of() : List.copyOf(sources);
    }

    /** Keeps Stage 1/2 call sites and tests compiling. */
    public AskResponse(String answer, Confidence confidence) {
        this(answer, confidence, List.of());
    }
}