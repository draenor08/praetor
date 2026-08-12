package com.praetor.problem.repository;

import com.praetor.problem.entity.ProblemView;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

/** Read-only access for the problem-read shim. See {@link ProblemView}. */
@Repository
public interface ProblemViewRepository extends JpaRepository<ProblemView, Long> {

    /** Staff list: archived problems are retired from it (their detail page still resolves). */
    List<ProblemView> findAllByArchivedFalseOrderByDifficultyAscTitleAsc();

    /**
     * The contestant-facing list — the staff list minus every problem under contest embargo (any
     * contest using it has not ended yet). Filtered in SQL rather than in Java so an embargoed row
     * never leaves the database; the same rule is enforced per-problem by
     * {@code ContestAccessService}, which this query deliberately mirrors.
     */
    @Query(value = """
            SELECT p.* FROM problems p
            WHERE p.archived = false
              AND NOT EXISTS (
                SELECT 1 FROM contest_problems cp
                JOIN contests c ON c.id = cp.contest_id
                WHERE cp.problem_id = p.id
                  AND now() < c.ends_at)
            ORDER BY p.difficulty ASC, p.title ASC
            """, nativeQuery = true)
    List<ProblemView> findPublicListOrderByDifficultyAscTitleAsc();

    Optional<ProblemView> findBySlug(String slug);
}
