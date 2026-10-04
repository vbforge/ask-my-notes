package com.vbforge.asknotes.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "notes")
public record NotesProperties(
        @NotBlank String directory,
        @DefaultValue("1200") @Min(200) int chunkMaxChars
) {
}