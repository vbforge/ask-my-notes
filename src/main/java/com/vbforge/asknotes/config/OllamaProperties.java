package com.vbforge.asknotes.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Why: a record gives immutable, type-safe config, and Duration binds 2s and 90s directly.
 * @Validated makes the app fail at startup if a value is missing or blank,
 * which is better than a confusing NullPointerException on the first request.
 * */

@Validated
@ConfigurationProperties(prefix = "ollama")
public record OllamaProperties(
        @NotBlank String baseUrl,
        @NotBlank String chatModel,
        @NotBlank String embeddingModel,
        @Positive int embeddingDimensions,
        @NotNull String documentPrefix,     // may be empty for models that need no prefix
        @NotNull String queryPrefix,
        @NotNull Duration connectTimeout,
        @NotNull Duration readTimeout
) {

    @AssertTrue(message = "ollama.read-timeout must be at least 1s (a bare number means milliseconds, write e.g. 90s)")
    public boolean isReadTimeoutReasonable() {
        return readTimeout == null || readTimeout.compareTo(Duration.ofSeconds(1)) >= 0;
    }

}


