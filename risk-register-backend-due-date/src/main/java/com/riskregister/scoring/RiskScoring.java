package com.riskregister.scoring;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;

/**
 * scoring logic 

 * inherent = likelihood × impact                                   (1..25)
 *
 * Each mitigation with effectiveness e (1..5) leaves (1 − 0.15·e) of the risk in place:
 *      e=1 → 85%   e=2 → 70%   e=3 → 55%   e=4 → 40%   e=5 → 25%
 * Mitigations compound multiplicatively (diminishing returns, order independent):
 *      factor   = max( ∏ (1 − 0.15·eᵢ),  0.10 )                    (never remove more than 90%)
 *      residual = max( 1, round_half_up( inherent × factor ) )     (integer 1..25)
 * 
 *
 * Exact BigDecimal arithmetic is used so rounding is deterministic (no 8.499999 surprises).
 */
public final class RiskScoring {

    public static final int MIN_RATING = 1;
    public static final int MAX_RATING = 5;
    public static final int MIN_SCORE = 1;
    public static final int MAX_SCORE = 25;

    /** Each effectiveness point removes this many percent of the remaining risk. */
    static final int REDUCTION_PERCENT_PER_POINT = 15;

    /** Even perfect controls never remove more than 90% of the inherent risk. */
    static final BigDecimal MIN_RESIDUAL_FACTOR = new BigDecimal("0.10");

    private RiskScoring() {}

    public static int inherentScore(int likelihood, int impact) {
        requireRating("likelihood", likelihood);
        requireRating("impact", impact);
        return likelihood * impact;
    }

    public static int residualScore(int inherentScore, Collection<Integer> mitigationEffectiveness) {
        if (inherentScore < MIN_SCORE || inherentScore > MAX_SCORE) {
            throw new IllegalArgumentException(
                    "Inherent score must be between " + MIN_SCORE + " and " + MAX_SCORE + " but was " + inherentScore);
        }
        BigDecimal factor = BigDecimal.ONE;
        for (int effectiveness : mitigationEffectiveness) {
            requireRating("effectiveness", effectiveness);
            BigDecimal remaining = BigDecimal.valueOf(100 - REDUCTION_PERCENT_PER_POINT * effectiveness).movePointLeft(2);
            factor = factor.multiply(remaining);
        }
        factor = factor.max(MIN_RESIDUAL_FACTOR);

        int residual = BigDecimal.valueOf(inherentScore)
                .multiply(factor)
                .setScale(0, RoundingMode.HALF_UP)
                .intValueExact();
        return Math.max(MIN_SCORE, residual);
    }

    private static void requireRating(String name, int value) {
        if (value < MIN_RATING || value > MAX_RATING) {
            throw new IllegalArgumentException(
                    name + " must be an integer between " + MIN_RATING + " and " + MAX_RATING + " but was " + value);
        }
    }
}
