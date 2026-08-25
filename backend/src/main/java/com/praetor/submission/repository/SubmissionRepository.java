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

    /** Total number of submissions made by a user. */
    @Query("SELECT COUNT(s) FROM Submission s WHERE s.userId = :userId")
    long countByUserId(@Param("userId") Long userId);

    /** Number of distinct problems the user has solved (verdict = 'AC'). */
    @Query("SELECT COUNT(DISTINCT s.problemId) FROM Submission s WHERE s.userId = :userId AND s.verdict = 'AC'")
    long countDistinctSolvedProblemsByUser(@Param("userId") Long userId);
}
