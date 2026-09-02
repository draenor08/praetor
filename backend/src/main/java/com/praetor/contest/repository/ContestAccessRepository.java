package com.praetor.contest.repository;

import com.praetor.contest.entity.Contest;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * The two questions the contest embargo asks of the database. Bound to {@link Contest} only to
 * satisfy Spring Data's type parameter; both queries are native so the contest module answers for
 * problems without referencing the problem module (same shape as {@link StandingsRepository} over
 * {@code submissions}).
 *
 * <p>Both use Postgres {@code now()} — the server clock, so a skewed client cannot argue its way
 * into an embargoed statement.
 */
@Repository
public interface ContestAccessRepository extends JpaRepository<Contest, Long> {

    /**
     * True while the problem belongs to a contest that has not ended — upcoming OR running. This is
     * the embargo itself: such a problem is withheld from the public list and its statement is
     * refused, until the last contest using it is over.
     */
    @Query(value = """
            SELECT EXISTS (
              SELECT 1 FROM contest_problems cp
              JOIN contests c ON c.id = cp.contest_id
              WHERE cp.problem_id = :problemId
                AND now() < c.ends_at)
            """, nativeQuery = true)
    boolean existsUnendedContestForProblem(@Param("problemId") Long problemId);

    /**
     * True if the user is registered for a contest that is RUNNING right now and uses this problem.
     * Registration alone is deliberately not enough — the window must have opened, so registering
     * early buys no head start on the statements.
     */
    @Query(value = """
            SELECT EXISTS (
              SELECT 1 FROM contest_problems cp
              JOIN contests c ON c.id = cp.contest_id
              JOIN registrations r ON r.contest_id = c.id AND r.user_id = :userId
              WHERE cp.problem_id = :problemId
                AND now() BETWEEN c.starts_at AND c.ends_at)
            """, nativeQuery = true)
    boolean existsRunningRegisteredContestForProblem(@Param("problemId") Long problemId,
                                                     @Param("userId") Long userId);

    /**
     * The contest a new submission to this problem scores in, or {@code null} for practice: the
     * contest that is RUNNING right now, uses this problem, and that the user is registered for.
     * Deliberately the same three conditions as
     * {@link #existsRunningRegisteredContestForProblem} — that one answers "may this caller submit
     * at all", this one answers "and does it count" — so the two can never disagree about what a
     * live participation is.
     *
     * <p>A problem can belong to at most one contest ({@link #isEligibleForContest} only lets a
     * draft nobody has read be claimed, and publication is one-way), so the answer cannot be
     * ambiguous; {@code LIMIT 1} states that rather than relying on it.
     */
    @Query(value = """
            SELECT c.id
            FROM contest_problems cp
            JOIN contests c ON c.id = cp.contest_id
            JOIN registrations r ON r.contest_id = c.id AND r.user_id = :userId
            WHERE cp.problem_id = :problemId
              AND now() BETWEEN c.starts_at AND c.ends_at
            LIMIT 1
            """, nativeQuery = true)
    Long findRunningRegisteredContestForProblem(@Param("problemId") Long problemId,
                                                @Param("userId") Long userId);

    /**
     * True if a contest may use this problem: nobody has ever been able to read it, and no other
     * contest has already claimed it. The single source of the eligibility rule — the pool query
     * below selects on exactly the same two conditions.
     */
    @Query(value = """
            SELECT EXISTS (
              SELECT 1 FROM problems p
              WHERE p.id = :problemId
                AND p.published_at IS NULL
                AND NOT EXISTS (SELECT 1 FROM contest_problems cp WHERE cp.problem_id = p.id))
            """, nativeQuery = true)
    boolean isEligibleForContest(@Param("problemId") Long problemId);

    /** Why a problem is not eligible, for a message worth reading. Null when it is eligible. */
    @Query(value = """
            SELECT CASE
                     WHEN p.id IS NULL THEN 'no such problem'
                     WHEN EXISTS (SELECT 1 FROM contest_problems cp WHERE cp.problem_id = p.id)
                       THEN 'already used by a contest'
                     WHEN p.published_at IS NOT NULL
                       THEN 'has been publicly visible since ' || to_char(p.published_at, 'YYYY-MM-DD')
                     ELSE NULL
                   END
            FROM problems p WHERE p.id = :problemId
            """, nativeQuery = true)
    String ineligibleReason(@Param("problemId") Long problemId);

    /**
     * The eligible pool, for the contest creation page. Carries the counts an admin needs to judge
     * a draft at a glance — a problem with no test cases cannot be judged at all.
     */
    @Query(value = """
            SELECT p.id         AS problemId,
                   p.slug       AS slug,
                   p.title      AS title,
                   p.difficulty AS difficulty,
                   p.judge_mode AS judgeMode,
                   u.username   AS author,
                   (SELECT count(*) FROM test_cases tc WHERE tc.problem_id = p.id) AS testCases
            FROM problems p
            LEFT JOIN users u ON u.id = p.created_by
            WHERE p.published_at IS NULL
              AND NOT EXISTS (SELECT 1 FROM contest_problems cp WHERE cp.problem_id = p.id)
            ORDER BY p.difficulty ASC, p.title ASC
            """, nativeQuery = true)
    List<EligibleProblemRow> findEligiblePool();
}
