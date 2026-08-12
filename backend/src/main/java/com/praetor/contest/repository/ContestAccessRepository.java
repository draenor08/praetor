package com.praetor.contest.repository;

import com.praetor.contest.entity.Contest;
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
}
