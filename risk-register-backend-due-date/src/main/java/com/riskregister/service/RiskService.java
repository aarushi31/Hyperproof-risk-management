package com.riskregister.service;

import com.riskregister.domain.Risk;
import com.riskregister.domain.RiskPolicy;
import com.riskregister.domain.RiskStatus;
import com.riskregister.exception.NotFoundException;
import com.riskregister.repository.RiskRepository;
import com.riskregister.web.dto.RiskRequest;
import com.riskregister.web.dto.RiskResponse;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class RiskService {

    private final RiskRepository riskRepository;
    private final Clock clock;

    public RiskService(RiskRepository riskRepository, Clock clock) {
        this.riskRepository = riskRepository;
        this.clock = clock;
    }

    @Transactional
    public RiskResponse create(RiskRequest request) {
        RiskStatus status = request.status() != null ? request.status() : RiskStatus.OPEN;
        // A brand-new risk has no mitigations yet, so creating it as Closed is rejected by the policy.
        RiskPolicy.assertStatusAllowed(status, 0);

        Risk risk = new Risk();
        apply(risk, request);
        risk.setStatus(status);
        return RiskMapper.toResponse(riskRepository.save(risk), LocalDate.now(clock));
    }

    public List<RiskResponse> list(RiskQuery query) {
        Specification<Risk> spec = (root, q, cb) -> cb.conjunction();
        if (query.category() != null) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("category"), query.category()));
        }
        if (query.status() != null) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("status"), query.status()));
        }

        // Residual score is derived from mitigations, so sorting happens in memory after mapping.
        // Fine for a risk register (hundreds/low thousands of rows); see README for the scaling note.
        List<RiskResponse> responses = riskRepository.findAll(spec, Sort.by("id")).stream()
                .map(r -> RiskMapper.toResponse(r, LocalDate.now(clock)))
                .toList();

        Comparator<RiskResponse> comparator = switch (query.sortBy()) {
            case RESIDUAL -> Comparator.comparingInt(RiskResponse::residualScore)
                    .thenComparingInt(RiskResponse::inherentScore);
            case INHERENT -> Comparator.comparingInt(RiskResponse::inherentScore)
                    .thenComparingInt(RiskResponse::residualScore);
            case CREATED -> Comparator.comparing(RiskResponse::createdAt);
        };
        if (query.direction() == SortDirection.DESC) {
            comparator = comparator.reversed();
        }
        return responses.stream().sorted(comparator.thenComparing(RiskResponse::id)).toList();
    }

    public RiskResponse get(Long id) {
        return RiskMapper.toResponse(load(id), LocalDate.now(clock));
    }

    @Transactional
    public RiskResponse update(Long id, RiskRequest request) {
        Risk risk = load(id);
        RiskStatus resulting = request.status() != null ? request.status() : risk.getStatus();
        RiskPolicy.assertStatusAllowed(resulting, risk.getMitigations().size());

        apply(risk, request);
        risk.setStatus(resulting);
        return RiskMapper.toResponse(riskRepository.save(risk), LocalDate.now(clock));
    }

    @Transactional
    public void delete(Long id) {
        riskRepository.delete(load(id)); // mitigations are removed via cascade
    }

    private Risk load(Long id) {
        return riskRepository.findWithMitigationsById(id)
                .orElseThrow(() -> new NotFoundException("Risk " + id + " not found"));
    }

    private static void apply(Risk risk, RiskRequest r) {
        risk.setTitle(r.title().trim());
        risk.setDescription(r.description() == null || r.description().isBlank() ? null : r.description().trim());
        risk.setCategory(r.category());
        risk.setOwner(r.owner().trim());
        risk.setLikelihood(r.likelihood());
        risk.setImpact(r.impact());
        risk.setNextReviewDate(r.nextReviewDate());
    }
}
