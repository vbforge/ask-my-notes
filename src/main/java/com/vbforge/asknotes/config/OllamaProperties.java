package com.vbforge.asknotes.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
        @NotNull Duration connectTimeout,
        @NotNull Duration readTimeout
) {
}