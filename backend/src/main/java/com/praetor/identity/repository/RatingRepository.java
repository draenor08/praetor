package com.praetor.identity.repository;

import com.praetor.identity.entity.Rating;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface RatingRepository extends JpaRepository<Rating, Long> {

    long countByValueGreaterThan(Integer value);

    /**
     * One page of the leaderboard, ranked, in a single statement.
     *
     * <p>{@code RANK()} is evaluated over the whole table before {@code LIMIT}, so the ranks
     * are global rather than page-local, and equal ratings share a rank. Replaces a loop that
     * issued a {@code findById} plus a {@code countByValueGreaterThan} per row — 2N+1
     * statements for what is one indexed scan (see {@code idx_ratings_value}).
     */
    @Query(value = """
            SELECT RANK() OVER (ORDER BY r.value DESC) AS rank,
                   u.username AS handle,
                   r.value    AS rating
            FROM ratings r
            JOIN users u ON u.id = r.user_id
            ORDER BY r.value DESC, r.user_id
            LIMIT :size OFFSET :offset
            """, nativeQuery = true)
    List<LeaderboardRow> findLeaderboardPage(@Param("size") int size,
                                             @Param("offset") int offset);

    /** Projection for {@link #findLeaderboardPage}; mapped from the native query's aliases. */
    interface LeaderboardRow {
        long getRank();

        String getHandle();

        int getRating();
    }
}
