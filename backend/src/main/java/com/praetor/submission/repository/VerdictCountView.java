package com.praetor.submission.repository;

/**
 * Projection for the per-verdict tally behind a user's solve statistics (FR-25): one row per
 * verdict the user has actually received, with how many submissions carried it.
 *
 * <p>Deliberately a tally rather than a set of independent counters. Everything the stats response
 * needs except the distinct-problem count — total attempts, the accepted count, and the
 * {@code byVerdict} breakdown — is derived from these rows, so the contest-end filter that keeps
 * the standings freeze intact is written once instead of once per counter.
 */
public interface VerdictCountView {

    String getVerdict();

    long getTotal();
}
