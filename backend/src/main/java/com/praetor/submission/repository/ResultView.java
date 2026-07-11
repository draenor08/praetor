package com.praetor.submission.repository;

/**
 * Projection for the per-test-case rows of the GET-submission response: joins
 * {@code submission_results} with {@code test_cases} to surface each result in {@code ord} order.
 */
public interface ResultView {
    Integer getOrd();

    String getVerdict();

    Integer getTimeMs();

    Integer getMemKb();
}
