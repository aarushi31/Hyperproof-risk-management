package com.riskregister.web.dto;

import com.riskregister.domain.RiskCategory;
import com.riskregister.domain.RiskStatus;
import jakarta.validation.constraints.*;
import java.time.LocalDate;

/**
 * Body for create and update (PUT = full replacement of the editable fields).
 * {@code status} is optional: defaults to OPEN on create, unchanged on update.
 */
public record RiskRequest(
        @NotBlank(message = "title is required") @Size(max = 200, message = "title must be at most 200 characters")
        String title,

        @Size(max = 4000, message = "description must be at most 4000 characters")
        String description,

        @NotNull(message = "category is required")
        RiskCategory category,

        @NotBlank(message = "owner is required") @Size(max = 200, message = "owner must be at most 200 characters")
        String owner,

        @NotNull(message = "likelihood is required")
        @Min(value = 1, message = "likelihood must be an integer between 1 and 5")
        @Max(value = 5, message = "likelihood must be an integer between 1 and 5")
        Integer likelihood,

        @NotNull(message = "impact is required")
        @Min(value = 1, message = "impact must be an integer between 1 and 5")
        @Max(value = 5, message = "impact must be an integer between 1 and 5")
        Integer impact,

        /** Optional, ISO format YYYY-MM-DD. On update, omitting it clears the date (PUT = full replacement). */
        LocalDate nextReviewDate,

        RiskStatus status) {
}
