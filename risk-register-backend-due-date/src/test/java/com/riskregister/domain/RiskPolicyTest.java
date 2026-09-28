package com.riskregister.domain;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.riskregister.exception.BusinessRuleException;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RiskPolicyTest {

    @Test
    void cannotCloseWithoutMitigations() {
        assertThatThrownBy(() -> RiskPolicy.assertStatusAllowed(RiskStatus.CLOSED, 0))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(e -> ((BusinessRuleException) e).getCode())
                .isEqualTo(RiskPolicy.CLOSURE_REQUIRES_MITIGATION);
    }

    @Test
    void canCloseWithAtLeastOneMitigation() {
        assertThatCode(() -> RiskPolicy.assertStatusAllowed(RiskStatus.CLOSED, 1)).doesNotThrowAnyException();
        assertThatCode(() -> RiskPolicy.assertStatusAllowed(RiskStatus.CLOSED, 3)).doesNotThrowAnyException();
    }

    @Test
    void openAndMitigatingAreAlwaysAllowed() {
        assertThatCode(() -> RiskPolicy.assertStatusAllowed(RiskStatus.OPEN, 0)).doesNotThrowAnyException();
        assertThatCode(() -> RiskPolicy.assertStatusAllowed(RiskStatus.MITIGATING, 0)).doesNotThrowAnyException();
    }

    @Test
    void cannotRemoveLastMitigationOfClosedRisk() {
        assertThatThrownBy(() -> RiskPolicy.assertMitigationRemovable(RiskStatus.CLOSED, 1))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(e -> ((BusinessRuleException) e).getCode())
                .isEqualTo(RiskPolicy.LAST_MITIGATION_ON_CLOSED_RISK);
    }

    @Test
    void canRemoveMitigationWhenOthersRemainOrRiskNotClosed() {
        assertThatCode(() -> RiskPolicy.assertMitigationRemovable(RiskStatus.CLOSED, 2)).doesNotThrowAnyException();
        assertThatCode(() -> RiskPolicy.assertMitigationRemovable(RiskStatus.OPEN, 1)).doesNotThrowAnyException();
        assertThatCode(() -> RiskPolicy.assertMitigationRemovable(RiskStatus.MITIGATING, 1)).doesNotThrowAnyException();
    }

    // ---------- overdue reviews ----------

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 28);

    @Test
    void reviewIsOverdueOnlyAfterTheDatePasses() {
        assertThat(RiskPolicy.isOverdue(TODAY.minusDays(1), RiskStatus.OPEN, TODAY)).isTrue();
        assertThat(RiskPolicy.isOverdue(TODAY, RiskStatus.OPEN, TODAY)).isFalse();          // due today: not overdue yet
        assertThat(RiskPolicy.isOverdue(TODAY.plusDays(1), RiskStatus.OPEN, TODAY)).isFalse();
        assertThat(RiskPolicy.isOverdue(TODAY.minusYears(1), RiskStatus.MITIGATING, TODAY)).isTrue();
    }

    @Test
    void noReviewDateOrClosedRiskIsNeverOverdue() {
        assertThat(RiskPolicy.isOverdue(null, RiskStatus.OPEN, TODAY)).isFalse();
        assertThat(RiskPolicy.isOverdue(TODAY.minusDays(30), RiskStatus.CLOSED, TODAY)).isFalse();
    }
}
