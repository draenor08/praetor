package com.praetor.problem.repository;

import com.praetor.problem.entity.Problem;
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
}
