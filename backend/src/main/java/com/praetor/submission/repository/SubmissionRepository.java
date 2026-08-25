package com.praetor.submission.repository;

import com.praetor.submission.dto.SubmissionSummary;
import com.praetor.submission.entity.Submission;
import java.time.ZonedDateTime;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface SubmissionRepository extends JpaRepository<Submission, Long> {

    /** Rows stuck in a status past a cutoff — used by the reaper for crash recovery. */
    List<Submission> findByStatusAndCreatedAtBefore(String status, ZonedDateTime cutoff);

    /**
     * Non-admin callers may only see their own history, while staff can filter to any user or a
     * problem/contest subset. The summary rows intentionally omit source code and per-test results so
     * the list is a lightweight overview; the details endpoint stays private.
     */
    @Query("""
            SELECT new com.praetor.submission.dto.SubmissionSummary(
               s.id,
               u.username,
               jp.slug,
               s.language,
               s.status,
               s.verdict,
               s.score,
               s.createdAt
            )
            FROM Submission s
            JOIN com.praetor.identity.entity.User u ON u.id = s.userId
            JOIN com.praetor.submission.entity.JudgeProblem jp ON jp.id = s.problemId
            WHERE (:userId IS NULL OR s.userId = :userId)
             AND (:problemId IS NULL OR s.problemId = :problemId)
             AND (:contestId IS NULL OR s.contestId = :contestId)
            """)
    Page<SubmissionSummary> findHistoryPage(@Param("userId") Long userId,
                                          @Param("problemId") Long problemId,
                                          @Param("contestId") Long contestId,
                                          Pageable pageable);

    /**
     * True if the problem is currently used by a live contest (feat 3d guard). Uses Postgres
     * {@code now()} (server clock, no skew). Gates the practice-reveal so hidden test data for a
     * live-contest problem can't be farmed via a practice submission.
     */
    @Query(value = """
            SELECT EXISTS (
              SELECT 1 FROM contest_problems cp
              JOIN contests c ON c.id = cp.contest_id
              WHERE cp.problem_id = :problemId
                AND now() BETWEEN c.starts_at AND c.ends_at)
            """, nativeQuery = true)
    boolean existsLiveContestForProblem(@Param("problemId") Long problemId);

    /**
     * Per-verdict submission tally for a user, feeding the solve statistics (FR-25).
     *
     * <p><b>Excludes every submission belonging to a contest that has not ended yet.</b> The
     * standings freeze is a property of the system, not of the standings endpoint: this response is
     * readable by any authenticated user, so without that predicate a contestant could poll a
     * rival's handle during a freeze and watch the count tick up, learning what the board is
     * deliberately withholding. An aggregate count is a side channel like any other. Practice
     * submissions ({@code contest_id IS NULL}) always count.
     *
     * <p>Uses Postgres {@code now()} — the database clock, no application-side skew — matching
     * {@link #existsLiveContestForProblem(Long)}. Submissions still in flight have no verdict yet
     * and are excluded, so the tally sums to the number of judged attempts.
     */
    @Query(value = """
            SELECT s.verdict AS verdict, COUNT(*) AS total
            FROM submissions s
            WHERE s.user_id = :userId
              AND s.verdict IS NOT NULL
              AND (s.contest_id IS NULL
                   OR EXISTS (SELECT 1 FROM contests c
                              WHERE c.id = s.contest_id AND c.ends_at <= now()))
            GROUP BY s.verdict
            """, nativeQuery = true)
    List<VerdictCountView> tallyVerdictsForUser(@Param("userId") Long userId);

    /**
     * Distinct problems the user has solved. Carries the same contest-end filter as
     * {@link #tallyVerdictsForUser(Long)} and for the same reason — a rising solved count leaks a
     * frozen board just as readily as a rising submission count.
     *
     * <p>Separate from the tally because a {@code GROUP BY verdict} cannot also collapse to
     * distinct problems.
     */
    @Query(value = """
            SELECT COUNT(DISTINCT s.problem_id)
            FROM submissions s
            WHERE s.user_id = :userId
              AND s.verdict = 'AC'
              AND (s.contest_id IS NULL
                   OR EXISTS (SELECT 1 FROM contests c
                              WHERE c.id = s.contest_id AND c.ends_at <= now()))
            """, nativeQuery = true)
    long countDistinctSolvedProblems(@Param("userId") Long userId);
}
