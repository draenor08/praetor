package com.praetor.problem.repository;

import com.praetor.problem.entity.ProblemView;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/** Read-only access for the problem-read shim. See {@link ProblemView}. */
@Repository
public interface ProblemViewRepository extends JpaRepository<ProblemView, Long> {

    /** One row of the problem list, with its tags pre-aggregated to avoid a query per problem. */
    interface ProblemListRow {
        String getSlug();

        String getTitle();

        Integer getDifficulty();

        String getJudgeMode();

        /** Comma-separated tag names, alphabetical; null when the problem has no tags. */
        String getTags();
    }

    /**
     * The problem list, filtered (FR-15). One query for both audiences and every filter, because the
     * contest embargo lives in its {@code NOT EXISTS} clause: a second search-only query would be a
     * path on which embargoed problems leak while a contest runs. The same rule is enforced
     * per-problem by {@code ContestAccessService}, which this deliberately mirrors.
     *
     * <p>Every filter is inert at its empty value, so one query serves an unfiltered list too:
     * <ul>
     *   <li>{@code staff} — true skips the embargo clause; archived problems stay hidden either way,
     *       which is also what keeps unpublished drafts out of the list.
     *   <li>{@code q} — {@code ''} matches everything. Uses {@code position()} rather than
     *       {@code ILIKE} so a {@code %} or {@code _} typed into the search box is a literal
     *       character and not a wildcard.
     *   <li>{@code minDifficulty} / {@code maxDifficulty} — null means unbounded on that side.
     *   <li>{@code tagCsv} / {@code tagCount} — {@code ''}/0 matches everything. Tags are passed as
     *       one CSV string and split in SQL: binding an empty collection to an {@code IN} clause
     *       renders {@code IN ()}, which is a Postgres syntax error. Semantics are AND — a problem
     *       must carry every selected tag — which is why the match count is compared to
     *       {@code tagCount} rather than merely being non-zero.
     * </ul>
     */
    @Query(value = """
            SELECT p.slug       AS slug,
                   p.title      AS title,
                   p.difficulty AS difficulty,
                   p.judge_mode AS judgeMode,
                   (SELECT string_agg(t.name, ',' ORDER BY t.name)
                      FROM problem_tags pt
                      JOIN tags t ON t.id = pt.tag_id
                     WHERE pt.problem_id = p.id) AS tags
            FROM problems p
            WHERE p.archived = false
              AND (:staff = true
                   OR NOT EXISTS (
                     SELECT 1 FROM contest_problems cp
                     JOIN contests c ON c.id = cp.contest_id
                     WHERE cp.problem_id = p.id
                       AND now() < c.ends_at))
              AND (:q = ''
                   OR position(lower(:q) in lower(p.title)) > 0
                   OR position(lower(:q) in lower(p.slug)) > 0)
              AND (:minDifficulty IS NULL OR p.difficulty >= :minDifficulty)
              AND (:maxDifficulty IS NULL OR p.difficulty <= :maxDifficulty)
              AND (:tagCsv = ''
                   OR (SELECT count(DISTINCT t.name)
                         FROM problem_tags pt
                         JOIN tags t ON t.id = pt.tag_id
                        WHERE pt.problem_id = p.id
                          AND t.name = ANY(string_to_array(:tagCsv, ','))) = :tagCount)
            ORDER BY p.difficulty ASC, p.title ASC, p.id ASC
            LIMIT :size OFFSET :offset
            """, nativeQuery = true)
    List<ProblemListRow> search(@Param("staff") boolean staff,
                                @Param("q") String q,
                                @Param("minDifficulty") Integer minDifficulty,
                                @Param("maxDifficulty") Integer maxDifficulty,
                                @Param("tagCsv") String tagCsv,
                                @Param("tagCount") int tagCount,
                                @Param("size") int size,
                                @Param("offset") int offset);

    /**
     * How many problems the same filters match, for the page envelope. Deliberately a copy of
     * {@link #search}'s WHERE clause rather than a derived query: the embargo lives in that clause,
     * and a count that drifted from it would report rows the caller is not allowed to see.
     */
    @Query(value = """
            SELECT count(*)
            FROM problems p
            WHERE p.archived = false
              AND (:staff = true
                   OR NOT EXISTS (
                     SELECT 1 FROM contest_problems cp
                     JOIN contests c ON c.id = cp.contest_id
                     WHERE cp.problem_id = p.id
                       AND now() < c.ends_at))
              AND (:q = ''
                   OR position(lower(:q) in lower(p.title)) > 0
                   OR position(lower(:q) in lower(p.slug)) > 0)
              AND (:minDifficulty IS NULL OR p.difficulty >= :minDifficulty)
              AND (:maxDifficulty IS NULL OR p.difficulty <= :maxDifficulty)
              AND (:tagCsv = ''
                   OR (SELECT count(DISTINCT t.name)
                         FROM problem_tags pt
                         JOIN tags t ON t.id = pt.tag_id
                        WHERE pt.problem_id = p.id
                          AND t.name = ANY(string_to_array(:tagCsv, ','))) = :tagCount)
            """, nativeQuery = true)
    long countMatching(@Param("staff") boolean staff,
                       @Param("q") String q,
                       @Param("minDifficulty") Integer minDifficulty,
                       @Param("maxDifficulty") Integer maxDifficulty,
                       @Param("tagCsv") String tagCsv,
                       @Param("tagCount") int tagCount);

    Optional<ProblemView> findBySlug(String slug);

    /**
     * Has this user ever had an accepted submission for this problem? Gates the editorial (FR-16):
     * a solution is only shown to someone who has already produced one. Native and self-contained,
     * so the problem module answers a question about {@code submissions} without depending on the
     * submission module's entities — the same shape as the embargo query living in the contest module.
     */
    @Query(value = """
            SELECT EXISTS (
              SELECT 1 FROM submissions s
              WHERE s.problem_id = :problemId
                AND s.user_id = :userId
                AND s.verdict = 'AC')
            """, nativeQuery = true)
    boolean existsAcceptedSubmission(@Param("problemId") Long problemId,
                                     @Param("userId") Long userId);
}
