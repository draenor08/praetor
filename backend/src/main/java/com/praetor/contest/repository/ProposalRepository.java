package com.praetor.contest.repository;

import com.praetor.contest.entity.ContestProblemProposal;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ProposalRepository extends JpaRepository<ContestProblemProposal, Long> {

    Optional<ContestProblemProposal> findByContestIdAndProblemId(Long contestId, Long problemId);

    boolean existsByContestIdAndProblemId(Long contestId, Long problemId);

    /**
     * A contest's proposals, each carrying the problem and setter it names. Native so the contest
     * module can read the problem and identity tables without mapping their entities.
     */
    @Query(value = """
            SELECT pr.id          AS id,
                   pr.problem_id  AS problemId,
                   p.slug         AS slug,
                   p.title        AS title,
                   p.difficulty   AS difficulty,
                   p.judge_mode   AS judgeMode,
                   u.username     AS proposedBy,
                   pr.status      AS status,
                   pr.note        AS note,
                   pr.created_at  AS createdAt,
                   (SELECT count(*) FROM test_cases tc WHERE tc.problem_id = p.id) AS testCases
            FROM contest_problem_proposals pr
            JOIN problems p ON p.id = pr.problem_id
            JOIN users u    ON u.id = pr.proposed_by
            WHERE pr.contest_id = :contestId
            ORDER BY pr.created_at ASC
            """, nativeQuery = true)
    List<ProposalRow> findRowsByContestId(@Param("contestId") Long contestId);

    /** Everything one setter has offered, newest first — their side of the same story. */
    @Query(value = """
            SELECT pr.id          AS id,
                   pr.problem_id  AS problemId,
                   p.slug         AS slug,
                   p.title        AS title,
                   p.difficulty   AS difficulty,
                   p.judge_mode   AS judgeMode,
                   u.username     AS proposedBy,
                   pr.status      AS status,
                   pr.note        AS note,
                   pr.created_at  AS createdAt,
                   (SELECT count(*) FROM test_cases tc WHERE tc.problem_id = p.id) AS testCases
            FROM contest_problem_proposals pr
            JOIN problems p ON p.id = pr.problem_id
            JOIN users u    ON u.id = pr.proposed_by
            WHERE pr.proposed_by = :userId
            ORDER BY pr.created_at DESC
            """, nativeQuery = true)
    List<ProposalRow> findRowsByProposer(@Param("userId") Long userId);
}
