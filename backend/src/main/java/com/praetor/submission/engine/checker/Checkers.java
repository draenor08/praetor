package com.praetor.submission.engine.checker;

/**
 * Resolves a {@link Checker} from a problem's {@code judge_mode}. Returns null for SPECIAL (custom
 * checker — out of scope) and any unknown mode, so the orchestrator can reject those cleanly.
 */
public final class Checkers {

    /** Fallback epsilon for a FLOAT problem whose {@code float_eps} column is null. */
    private static final double DEFAULT_EPS = 1e-6;

    private Checkers() {
    }

    public static Checker of(String judgeMode, Double floatEps) {
        if (judgeMode == null) {
            return null;
        }
        return switch (judgeMode) {
            case "EXACT" -> new ExactChecker();
            case "TOKEN" -> new TokenChecker();
            case "FLOAT" -> new FloatChecker(floatEps != null ? floatEps : DEFAULT_EPS);
            default -> null; // SPECIAL or unknown
        };
    }
}
