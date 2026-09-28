package com.riskregister.repository;

import com.riskregister.domain.Risk;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface RiskRepository extends JpaRepository<Risk, Long>, JpaSpecificationExecutor<Risk> {

    @EntityGraph(attributePaths = "mitigations")
    Optional<Risk> findWithMitigationsById(Long id);

    /** Filtered list with mitigations fetched in the same query (avoids N+1 when computing residual scores). */
    @Override
    @EntityGraph(attributePaths = "mitigations")
    List<Risk> findAll(Specification<Risk> spec, Sort sort);
}
