package com.praetor.identity.repository;

import com.praetor.identity.entity.RatingHistory;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

/**
 * Finds the contests whose ratings still need applying.
 *
 * <p>Native, and reading the contest module's tables directly, for the usual insulation
 * reason: identity must not depend on the contest module's entities to answer a question the
 * database can answer in one indexed pass.
 */
public interface RatedContestRepository extends Repository<RatingHistory, Long> {

    /**
     * Ended contests that had at least one registrant and have no rating history yet.
     *
     * <p>All three clauses matter. Without {@code ends_at <= now()} it would rate a running
     * contest; without the {@code registrations} clause a contest that ended with nobody in it
     * would produce no history rows and therefore stay "unrated" forever, re-scanned on every
     * tick; without the {@code rating_history} clause it would re-rate everything, every time.
     */
    @Query(value = """
            SELECT c.id FROM contests c
            WHERE c.ends_at <= now()
              AND EXISTS (SELECT 1 FROM registrations r WHERE r.contest_id = c.id)
              AND NOT EXISTS (SELECT 1 FROM rating_history rh WHERE rh.contest_id = c.id)
            ORDER BY c.ends_at
            """, nativeQuery = true)
    List<Long> findContestIdsAwaitingRating();
}
