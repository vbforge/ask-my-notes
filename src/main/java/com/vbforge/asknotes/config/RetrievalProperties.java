package com.vbforge.asknotes.config;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "retrieval")
public record RetrievalProperties(
        @DefaultValue("4") @Min(1) @Max(20) int topK,
        @DecimalMin("0.0") @DecimalMax("2.0") double maxDistance
) {
}