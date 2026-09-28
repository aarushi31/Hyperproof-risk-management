package com.riskregister.web.dto;

import jakarta.validation.constraints.*;

public record MitigationRequest(
        @NotBlank(message = "description is required")
        @Size(max = 2000, message = "description must be at most 2000 characters")
        String description,

        @NotNull(message = "effectiveness is required")
        @Min(value = 1, message = "effectiveness must be an integer between 1 and 5")
        @Max(value = 5, message = "effectiveness must be an integer between 1 and 5")
        Integer effectiveness) {
}
