package com.riskregister.domain;

import com.riskregister.exception.BusinessRuleException;
import java.time.LocalDate;

/**
 * Domain rules about risk lifecycle. Kept free of Spring/JPA so it is trivially unit-testable.
 *
 * Invariant enforced: a CLOSED risk always has at least one mitigation.
 */
public final class RiskPolicy {

    public static final String CLOSURE_REQUIRES_MITIGATION = "RISK_CLOSURE_REQUIRES_MITIGATION";
    public static final String LAST_MITIGATION_ON_CLOSED_RISK = "LAST_MITIGATION_ON_CLOSED_RISK";

    private RiskPolicy() {}

    /** Called whenever a risk is saved with the given (resulting) status. */
    public static void assertStatusAllowed(RiskStatus resultingStatus, int mitigationCount) {
        if (resultingStatus == RiskStatus.CLOSED && mitigationCount == 0) {
            throw new BusinessRuleException(CLOSURE_REQUIRES_MITIGATION,
                    "A risk cannot be Closed while it has no mitigations. "
                            + "Add at least one mitigation first, or keep the risk Open/Mitigating.");
        }
    }

    /**
     * A review is overdue once its date has passed (due today is not yet overdue).
     * Closed risks are never overdue: nothing is left to review.
     */
    public static boolean isOverdue(LocalDate nextReviewDate, RiskStatus status, LocalDate today) {
        return nextReviewDate != null && status != RiskStatus.CLOSED && nextReviewDate.isBefore(today);
    }

    /** Called before a mitigation is deleted; protects the invariant above. */
    public static void assertMitigationRemovable(RiskStatus currentStatus, int currentMitigationCount) {
        if (currentStatus == RiskStatus.CLOSED && currentMitigationCount <= 1) {
            throw new BusinessRuleException(LAST_MITIGATION_ON_CLOSED_RISK,
                    "Cannot delete the last mitigation of a Closed risk. "
                            + "Re-open the risk first, or add another mitigation before deleting this one.");
        }
    }
}
