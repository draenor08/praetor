package com.praetor.contest.repository;

import java.time.Instant;

/**
 * Read-only projection of the columns standings needs from the {@code submissions} table — kept as
 * a native-query projection (not a JPA entity) so the contest slice never imports the submission
 * slice's write {@code @Entity}. This is the module-insulation boundary: contest reads submission
 * verdicts through SQL, not through Java types owned by another slice.
 */
public interface StandingsSubmissionRow {

    Long getUserId();

    Long getProblemId();

    String getVerdict();

    Instant getCreatedAt();
}
