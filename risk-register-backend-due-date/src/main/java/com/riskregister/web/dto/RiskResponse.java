package com.riskregister.web.dto;

import com.riskregister.domain.RiskCategory;
import com.riskregister.domain.RiskStatus;
import com.riskregister.scoring.Severity;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record RiskResponse(
        Long id,
        String title,
        String description,
        RiskCategory category,
        String owner,
        int likelihood,
        int impact,
        RiskStatus status,
        LocalDate nextReviewDate,
        boolean overdue,
        int inherentScore,
        Severity inherentSeverity,
        int residualScore,
        Severity residualSeverity,
        int mitigationCount,
        List<MitigationResponse> mitigations,
        Instant createdAt,
        Instant updatedAt) {
}
