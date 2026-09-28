package com.riskregister.scoring;

/** Severity band for a 1–25 risk score. Applied to both inherent and residual scores. */
public enum Severity {
    LOW(1, 5),
    MEDIUM(6, 12),
    HIGH(13, 19),
    CRITICAL(20, 25);

    private final int min;
    private final int max;

    Severity(int min, int max) {
        this.min = min;
        this.max = max;
    }

    public int min() { return min; }
    public int max() { return max; }

    public static Severity fromScore(int score) {
        for (Severity s : values()) {
            if (score >= s.min && score <= s.max) {
                return s;
            }
        }
        throw new IllegalArgumentException(
                "Risk score must be between " + RiskScoring.MIN_SCORE + " and " + RiskScoring.MAX_SCORE
                        + " but was " + score);
    }
}
