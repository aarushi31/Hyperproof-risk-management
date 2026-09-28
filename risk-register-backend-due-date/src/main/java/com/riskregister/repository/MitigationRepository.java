package com.riskregister.repository;

import com.riskregister.domain.Mitigation;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MitigationRepository extends JpaRepository<Mitigation, Long> {
}
