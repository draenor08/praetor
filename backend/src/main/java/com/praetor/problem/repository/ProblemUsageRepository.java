package com.praetor.problem.repository;

import com.praetor.problem.entity.Problem;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * Native "is this problem in use?" queries backing the delete guards and the live-contest
 * edit freeze.
 *
 * <p>Native, not JPA associations, on purpose: the referencing tables ({@code submissions},
 * {@code contest_problems}, {@code clarifications}) belong to other modules, and the
 * insulation rule says a module reads foreign tables through a projection or a native query
 * rather than importing the owning module's entities. Four lines of duplicated SQL beat a
 * compile-time dependency between the problem and submission slices.
 */
public interface ProblemUsageRepository extends Repository<Problem, Long> {

    @Query(value = "SELECT count(*) FROM submissions WHERE problem_id = :problemId",
            nativeQuery = true)
    long countSubmissions(@Param("problemId") Long problemId);

    @Query(value = "SELECT count(*) FROM clarifications WHERE problem_id = :problemId",
            nativeQuery = true)
    long countClarifications(@Param("problemId") Long problemId);

    /** Title of any contest holding this problem (past, live or upcoming) — for the 409 body. */
    @Query(value = """
            SELECT c.title FROM contest_problems cp
            JOIN contests c ON c.id = cp.contest_id
            WHERE cp.problem_id = :problemId
            ORDER BY c.starts_at
            LIMIT 1
            """, nativeQuery = true)
    Optional<String> findUsingContestTitle(@Param("problemId") Long problemId);

    /**
     * True while a contest containing this problem is running. Same predicate as the
     * submission module's practice-reveal guard; duplicated rather than shared for the
     * insulation reason above.
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
     * The whole setter workspace list — every problem plus the counts that decide what the UI may
     * offer for it — in one statement.
     *
     * <p>Correlated subqueries rather than a per-row round trip: the workspace needs four facts
     * about each problem, and asking for them one problem at a time is the 2N+1 shape this codebase
     * has already been bitten by once. Archived problems sort last so the live set reads first.
     */
    @Query(value = """
            SELECT p.slug                AS slug,
                   p.title               AS title,
                   p.difficulty          AS difficulty,
                   p.judge_mode          AS judgeMode,
                   p.archived            AS archived,
                   (SELECT count(*) FROM test_cases t WHERE t.problem_id = p.id)     AS testCases,
                   (SELECT count(*) FROM submissions s WHERE s.problem_id = p.id)    AS submissions,
                   (SELECT count(*) FROM contest_problems cp WHERE cp.problem_id = p.id) AS contests,
                   (SELECT count(*) FROM clarifications cl WHERE cl.problem_id = p.id)   AS clarifications,
                   EXISTS (SELECT 1 FROM contest_problems cp
                           JOIN contests c ON c.id = cp.contest_id
                           WHERE cp.problem_id = p.id
                             AND now() BETWEEN c.starts_at AND c.ends_at)             AS inLiveContest
            FROM problems p
            ORDER BY p.archived, p.difficulty, p.title
            """, nativeQuery = true)
    List<ManagedProblemRow> findManagementRows();

    /** Projection for {@link #findManagementRows}; mapped from the query's aliases. */
    interface ManagedProblemRow {
        String getSlug();

        String getTitle();

        int getDifficulty();

        String getJudgeMode();

        boolean getArchived();

        long getTestCases();

        long getSubmissions();

        long getContests();

        long getClarifications();

        boolean getInLiveContest();
    }
}
