package com.praetor.contest.repository;

import com.praetor.contest.entity.Contest;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Standings read queries. Bound to {@link Contest} only to satisfy Spring Data's repository type
 * parameter; the real work is the native projection query over {@code submissions}. All judged
 * (status DONE) submissions for a contest, oldest first — the calculator folds them into the board
 * (in-window filtering, freeze cutoff, and ICPC penalty all happen in Java so the one query serves
 * both the live and frozen views).
 */
@Repository
public interface StandingsRepository extends JpaRepository<Contest, Long> {

    @Query(value = """
            SELECT s.user_id    AS userId,
                   s.problem_id AS problemId,
                   s.verdict    AS verdict,
                   s.created_at AS createdAt
            FROM submissions s
            WHERE s.contest_id = :contestId AND s.status = 'DONE'
            ORDER BY s.created_at ASC
            """, nativeQuery = true)
    List<StandingsSubmissionRow> findJudged(@Param("contestId") Long contestId);
}
