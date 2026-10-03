package com.vbforge.asknotes.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Why: this is the input validation from the Stage 1 goals.
 * @NotBlank rejects empty and whitespace-only questions.
 * The 2000-character cap stops someone pasting a novel into a small model with a limited context window.
 * The controller will enforce both with @Valid.
 * */

public record AskRequest(
        @NotBlank(message = "question must not be blank")
        @Size(max = 2000, message = "question must be at most 2000 characters")
        String question
) {
}
