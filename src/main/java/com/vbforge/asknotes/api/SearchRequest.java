package com.vbforge.asknotes.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SearchRequest(
        @NotBlank(message = "question must not be blank")
        @Size(max = 2000, message = "question must be at most 2000 characters")
        String question,

        @Min(value = 1, message = "k must be between 1 and 20")
        @Max(value = 20, message = "k must be between 1 and 20")
        Integer k       // optional; the configured top-k is used when absent
) {
}