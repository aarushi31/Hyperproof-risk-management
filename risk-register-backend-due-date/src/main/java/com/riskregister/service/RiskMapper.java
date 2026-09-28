package com.riskregister.service;

import com.riskregister.domain.Mitigation;
import com.riskregister.domain.Risk;
import com.riskregister.scoring.RiskScoring;
import com.riskregister.scoring.Severity;
import com.riskregister.web.dto.MitigationResponse;
import com.riskregister.web.dto.RiskResponse;
import com.riskregister.domain.RiskPolicy;
import java.time.LocalDate;
import java.util.List;

/** Maps entities to API responses. Scores are always derived here, never stored. */
public final class RiskMapper {

    private RiskMapper() {}

    public static RiskResponse toResponse(Risk risk, LocalDate today) {
        List<Integer> effectiveness = risk.getMitigations().stream().map(Mitigation::getEffectiveness).toList();
        int inherent = RiskScoring.inherentScore(risk.getLikelihood(), risk.getImpact());
        int residual = RiskScoring.residualScore(inherent, effectiveness);
        return new RiskResponse(
                risk.getId(),
                risk.getTitle(),
                risk.getDescription(),
                risk.getCategory(),
                risk.getOwner(),
                risk.getLikelihood(),
                risk.getImpact(),
                risk.getStatus(),
                risk.getNextReviewDate(),
                RiskPolicy.isOverdue(risk.getNextReviewDate(), risk.getStatus(), today),
                inherent,
                Severity.fromScore(inherent),
                residual,
                Severity.fromScore(residual),
                risk.getMitigations().size(),
                risk.getMitigations().stream().map(RiskMapper::toResponse).toList(),
                risk.getCreatedAt(),
                risk.getUpdatedAt());
    }

    public static MitigationResponse toResponse(Mitigation m) {
        return new MitigationResponse(m.getId(), m.getRisk().getId(), m.getDescription(),
                m.getEffectiveness(), m.getCreatedAt());
    }
}
