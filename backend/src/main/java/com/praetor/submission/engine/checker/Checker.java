package com.praetor.submission.engine.checker;

/**
 * Decides whether a program's actual output matches the expected output for one test case — the
 * AC-vs-WA question only. Everything else (TLE/MLE/RE/CE) is decided upstream in the evaluator;
 * a checker is consulted only for a clean, in-limits run.
 *
 * <p>One implementation per {@code problems.judge_mode}: EXACT (byte-ish), TOKEN (whitespace-
 * insensitive), FLOAT (numeric within epsilon). SPECIAL (custom checker) is out of scope.
 */
public interface Checker {

    boolean matches(String actual, String expected);
}
