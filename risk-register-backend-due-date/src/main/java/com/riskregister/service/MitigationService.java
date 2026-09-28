package com.riskregister.service;

import com.riskregister.domain.Mitigation;
import com.riskregister.domain.Risk;
import com.riskregister.domain.RiskPolicy;
import com.riskregister.exception.NotFoundException;
import com.riskregister.repository.MitigationRepository;
import com.riskregister.repository.RiskRepository;
import com.riskregister.web.dto.MitigationRequest;
import com.riskregister.web.dto.MitigationResponse;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class MitigationService {

    private final RiskRepository riskRepository;
    private final MitigationRepository mitigationRepository;

    public MitigationService(RiskRepository riskRepository, MitigationRepository mitigationRepository) {
        this.riskRepository = riskRepository;
        this.mitigationRepository = mitigationRepository;
    }

    public List<MitigationResponse> list(Long riskId) {
        return loadRisk(riskId).getMitigations().stream().map(RiskMapper::toResponse).toList();
    }

    @Transactional
    public MitigationResponse create(Long riskId, MitigationRequest request) {
        Risk risk = loadRisk(riskId);
        Mitigation mitigation = new Mitigation();
        mitigation.setRisk(risk);
        mitigation.setDescription(request.description().trim());
        mitigation.setEffectiveness(request.effectiveness());
        mitigation = mitigationRepository.saveAndFlush(mitigation);
        risk.getMitigations().add(mitigation);
        return RiskMapper.toResponse(mitigation);
    }

    @Transactional
    public MitigationResponse update(Long riskId, Long mitigationId, MitigationRequest request) {
        Mitigation mitigation = find(loadRisk(riskId), mitigationId);
        mitigation.setDescription(request.description().trim());
        mitigation.setEffectiveness(request.effectiveness());
        return RiskMapper.toResponse(mitigation);
    }

    @Transactional
    public void delete(Long riskId, Long mitigationId) {
        Risk risk = loadRisk(riskId);
        Mitigation mitigation = find(risk, mitigationId);
        RiskPolicy.assertMitigationRemovable(risk.getStatus(), risk.getMitigations().size());
        risk.getMitigations().remove(mitigation); // orphanRemoval deletes the row
    }

    private Risk loadRisk(Long riskId) {
        return riskRepository.findWithMitigationsById(riskId)
                .orElseThrow(() -> new NotFoundException("Risk " + riskId + " not found"));
    }

    private static Mitigation find(Risk risk, Long mitigationId) {
        return risk.getMitigations().stream()
                .filter(m -> m.getId().equals(mitigationId))
                .findFirst()
                .orElseThrow(() -> new NotFoundException(
                        "Mitigation " + mitigationId + " not found on risk " + risk.getId()));
    }
}
