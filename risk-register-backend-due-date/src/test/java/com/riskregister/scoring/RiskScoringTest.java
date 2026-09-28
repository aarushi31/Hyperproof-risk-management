package com.riskregister.scoring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class RiskScoringTest {

    // ---------- inherent ----------

    @ParameterizedTest(name = "{0} x {1} = {2}")
    @CsvSource({"1,1,1", "1,5,5", "5,1,5", "3,4,12", "2,5,10", "5,5,25"})
    void inherentIsLikelihoodTimesImpact(int likelihood, int impact, int expected) {
        assertThat(RiskScoring.inherentScore(likelihood, impact)).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 0, 6, 100})
    void inherentRejectsOutOfRangeLikelihoodAndImpact(int bad) {
        assertThatThrownBy(() -> RiskScoring.inherentScore(bad, 3))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("likelihood");
        assertThatThrownBy(() -> RiskScoring.inherentScore(3, bad))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("impact");
    }

    // ---------- residual: the sanity checks from the brief ----------

    @Test
    void zeroMitigationsMeansResidualEqualsInherent() {
        for (int inherent = 1; inherent <= 25; inherent++) {
            assertThat(RiskScoring.residualScore(inherent, List.of())).isEqualTo(inherent);
        }
    }

    @Test
    void oneHighlyEffectiveMitigationLowersScoreMeaningfully() {
        assertThat(RiskScoring.residualScore(20, List.of(5))).isEqualTo(5);   // 20 * 0.25
        assertThat(RiskScoring.residualScore(25, List.of(5))).isEqualTo(6);   // 6.25 -> 6
        assertThat(RiskScoring.residualScore(16, List.of(5))).isEqualTo(4);
    }

    @Test
    void residualNeverFallsBelowOne() {
        assertThat(RiskScoring.residualScore(1, List.of(5))).isEqualTo(1);
        assertThat(RiskScoring.residualScore(1, List.of(5, 5, 5))).isEqualTo(1);
        assertThat(RiskScoring.residualScore(2, List.of(5, 5))).isEqualTo(1);
        for (int inherent = 1; inherent <= 25; inherent++) {
            assertThat(RiskScoring.residualScore(inherent, List.of(5, 5, 5, 5, 5))).isGreaterThanOrEqualTo(1);
        }
    }

    // ---------- residual: formula behaviour ----------

    @ParameterizedTest(name = "inherent 20, effectiveness {0} -> {1}")
    @CsvSource({"1,17", "2,14", "3,11", "4,8", "5,5"})
    void singleMitigationReductionTable(int effectiveness, int expectedResidual) {
        assertThat(RiskScoring.residualScore(20, List.of(effectiveness))).isEqualTo(expectedResidual);
    }

    @Test
    void moreEffectiveMitigationNeverGivesHigherResidual() {
        for (int inherent = 1; inherent <= 25; inherent++) {
            int previous = Integer.MAX_VALUE;
            for (int e = 1; e <= 5; e++) {
                int residual = RiskScoring.residualScore(inherent, List.of(e));
                assertThat(residual).isLessThanOrEqualTo(previous).isLessThanOrEqualTo(inherent);
                previous = residual;
            }
        }
    }

    @Test
    void addingAMitigationNeverIncreasesResidual() {
        for (int inherent = 1; inherent <= 25; inherent++) {
            for (int e1 = 1; e1 <= 5; e1++) {
                for (int e2 = 1; e2 <= 5; e2++) {
                    assertThat(RiskScoring.residualScore(inherent, List.of(e1, e2)))
                            .isLessThanOrEqualTo(RiskScoring.residualScore(inherent, List.of(e1)));
                }
            }
        }
    }

    @Test
    void mitigationsCompoundMultiplicatively() {
        // 25 * 0.55 * 0.55 = 7.5625 -> 8 (vs 14 with a single e=3 control)
        assertThat(RiskScoring.residualScore(25, List.of(3))).isEqualTo(14);
        assertThat(RiskScoring.residualScore(25, List.of(3, 3))).isEqualTo(8);
    }

    @Test
    void orderOfMitigationsDoesNotMatter() {
        assertThat(RiskScoring.residualScore(20, List.of(1, 4, 5)))
                .isEqualTo(RiskScoring.residualScore(20, List.of(5, 4, 1)))
                .isEqualTo(RiskScoring.residualScore(20, List.of(4, 1, 5)));
    }

    @Test
    void reductionIsCappedAtNinetyPercent() {
        // 25 * 0.10 = 2.5 -> 3 (HALF_UP). Piling on more perfect controls cannot go lower.
        assertThat(RiskScoring.residualScore(25, List.of(5, 5))).isEqualTo(3);
        assertThat(RiskScoring.residualScore(25, List.of(5, 5, 5, 5, 5, 5))).isEqualTo(3);
        assertThat(RiskScoring.residualScore(20, List.of(5, 5))).isEqualTo(2);
    }

    @Test
    void roundingIsHalfUpAndDeterministic() {
        assertThat(RiskScoring.residualScore(10, List.of(3))).isEqualTo(6);   // 5.5 -> 6
        assertThat(RiskScoring.residualScore(10, List.of(5))).isEqualTo(3);   // 2.5 -> 3
        assertThat(RiskScoring.residualScore(10, List.of(2))).isEqualTo(7);   // 7.0
    }

    @Test
    void residualNeverExceedsInherent() {
        for (int inherent = 1; inherent <= 25; inherent++) {
            for (int e = 1; e <= 5; e++) {
                assertThat(RiskScoring.residualScore(inherent, List.of(e))).isLessThanOrEqualTo(inherent);
            }
        }
    }

    @Test
    void residualRejectsInvalidInputs() {
        assertThatThrownBy(() -> RiskScoring.residualScore(0, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RiskScoring.residualScore(26, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RiskScoring.residualScore(10, List.of(0)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("effectiveness");
        assertThatThrownBy(() -> RiskScoring.residualScore(10, List.of(3, 6)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("effectiveness");
    }

    // ---------- severity bands ----------

    @ParameterizedTest(name = "score {0} -> {1}")
    @CsvSource({
            "1,LOW", "5,LOW",
            "6,MEDIUM", "12,MEDIUM",
            "13,HIGH", "19,HIGH",
            "20,CRITICAL", "25,CRITICAL"})
    void severityBandBoundaries(int score, Severity expected) {
        assertThat(Severity.fromScore(score)).isEqualTo(expected);
    }

    @Test
    void everyScoreFromOneToTwentyFiveHasExactlyOneBandAndBandsNeverDecrease() {
        Severity previous = Severity.LOW;
        for (int score = 1; score <= 25; score++) {
            Severity s = Severity.fromScore(score);
            assertThat(s.ordinal()).isGreaterThanOrEqualTo(previous.ordinal());
            assertThat(score).isBetween(s.min(), s.max());
            previous = s;
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {-5, 0, 26, 100})
    void severityRejectsScoresOutsideOneToTwentyFive(int score) {
        assertThatThrownBy(() -> Severity.fromScore(score)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void endToEndBandForInherentAndResidual() {
        int inherent = RiskScoring.inherentScore(5, 5);
        int residual = RiskScoring.residualScore(inherent, List.of(5));
        assertThat(Severity.fromScore(inherent)).isEqualTo(Severity.CRITICAL);
        assertThat(Severity.fromScore(residual)).isEqualTo(Severity.MEDIUM); // 6
    }
}
