package com.praetor.contest.repository;

import com.praetor.contest.entity.ContestProblem;
import com.praetor.contest.entity.ContestProblemId;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ContestProblemRepository extends JpaRepository<ContestProblem, ContestProblemId> {

    /** Problems of a contest in display order. Property path `id.contestId` through the @EmbeddedId. */
    List<ContestProblem> findByIdContestIdOrderByOrdAsc(Long contestId);

    /** Labels are unique per contest; checked before insert so the clash reads as 409, not 500. */
    boolean existsByIdContestIdAndLabel(Long contestId, String label);

    /**
     * The same slots, each carrying the slug and title its link needs. One join instead of a lookup
     * per slot; see {@link ContestProblemRow} for why it is native.
     */
    @Query(value = """
            SELECT cp.label      AS label,
                   cp.ord        AS ord,
                   cp.problem_id AS problemId,
                   p.slug        AS slug,
                   p.title       AS title
            FROM contest_problems cp
            JOIN problems p ON p.id = cp.problem_id
            WHERE cp.contest_id = :contestId
            ORDER BY cp.ord ASC
            """, nativeQuery = true)
    List<ContestProblemRow> findRowsByContestId(@Param("contestId") Long contestId);
}
