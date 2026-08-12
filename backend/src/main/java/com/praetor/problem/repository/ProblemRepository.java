package com.praetor.problem.repository;

import com.praetor.problem.entity.Problem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProblemRepository
        extends JpaRepository<Problem, Long> {

    Optional<Problem> findBySlug(String slug);

    boolean existsBySlug(String slug);

    List<Problem> findAllByOrderByDifficultyAscTitleAsc();

    /**
     * Draft problems whose contest is over. They were withheld for the contest, the contest has
     * run, so they belong in public practice now. Ordered by id so a partial failure resumes
     * predictably.
     */
    @Query(value = """
            SELECT p.* FROM problems p
            WHERE p.published_at IS NULL
              AND EXISTS (
                SELECT 1 FROM contest_problems cp
                JOIN contests c ON c.id = cp.contest_id
                WHERE cp.problem_id = p.id
                  AND c.ends_at <= now())
            ORDER BY p.id
            """, nativeQuery = true)
    List<Problem> findUnpublishedFromEndedContests();
}
