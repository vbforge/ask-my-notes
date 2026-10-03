package com.vbforge.asknotes.api;

/**
 * Why: this is exactly the shape the page reads: answer and confidence.
 * In Stage 3 we'll add a sources list here, and the page will already render it.
 * */

public record AskResponse(
        String answer,
        Confidence confidence
) {
}
