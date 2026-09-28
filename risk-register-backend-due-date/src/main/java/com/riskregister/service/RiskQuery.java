package com.riskregister.service;

import com.riskregister.domain.RiskCategory;
import com.riskregister.domain.RiskStatus;

/** Filters (nullable = no filter) and sort for listing risks. */
public record RiskQuery(RiskCategory category, RiskStatus status, SortField sortBy, SortDirection direction) {
}
