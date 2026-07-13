package com.praetor.submission.repository;

/**
 * Projection for the practice-mode reveal (feat 3d): the first failing test case's input, expected
 * output, and the program's captured actual output. Deliberately SEPARATE from {@link ResultView}
 * so the per-row list query never carries hidden test-case data — this projection is only ever
 * queried after the read-path security gate confirms the submission is a practice (non-contest) run.
 */
public interface RevealView {
    Integer getOrd();

    String getInput();

    String getExpected();

    String getActualOutput();
}
