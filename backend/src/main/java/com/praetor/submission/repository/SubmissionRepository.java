package com.praetor.submission.repository;

import com.praetor.submission.entity.Submission;
import java.time.ZonedDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface SubmissionRepository extends JpaRepository<Submission, Long> {

    /** Rows stuck in a status past a cutoff — used by the reaper for crash recovery. */
    List<Submission> findByStatusAndCreatedAtBefore(String status, ZonedDateTime cutoff);

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
}
