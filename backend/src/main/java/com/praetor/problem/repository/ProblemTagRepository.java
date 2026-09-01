package com.praetor.problem.repository;

import com.praetor.problem.entity.ProblemView;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Tags (FR-14). The {@code tags} and {@code problem_tags} tables carry no JPA associations: adding a
 * {@code @ManyToMany} to the write entity would make every problem read drag its tags along and put
 * a mutable collection in the middle of the create/update path. Native statements keep the join
 * table explicit and the entity unchanged.
 *
 * <p>Bound to {@link ProblemView} only to satisfy Spring Data's type parameter — no query here reads
 * or writes a problem row.
 *
 * <p>The write side takes tag names as ONE comma-separated string rather than a collection: binding
 * an empty collection to an {@code IN} list renders {@code IN ()}, which is a Postgres syntax error.
 * The list filter does the same thing for the same reason. Tag names cannot contain a comma —
 * {@code ProblemService.normalizeTags} refuses them — which is what makes the round trip safe.
 */
@Repository
public interface ProblemTagRepository extends JpaRepository<ProblemView, Long> {

    /** Every known tag, for the filter control's options. */
    @Query(value = "SELECT name FROM tags ORDER BY name", nativeQuery = true)
    List<String> findAllTagNames();

    @Query(value = """
            SELECT t.name FROM problem_tags pt
            JOIN tags t ON t.id = pt.tag_id
            WHERE pt.problem_id = :problemId
            ORDER BY t.name
            """, nativeQuery = true)
    List<String> findTagNamesByProblemId(@Param("problemId") Long problemId);

    /**
     * Creates whichever of these tags do not exist yet, in one statement. {@code ON CONFLICT DO
     * NOTHING} keeps it idempotent, so two setters introducing the same new tag cannot collide on
     * the unique index.
     */
    @Modifying
    @Query(value = """
            INSERT INTO tags (name)
            SELECT DISTINCT unnest(string_to_array(:nameCsv, ','))
            ON CONFLICT (name) DO NOTHING
            """, nativeQuery = true)
    void insertTagsIfAbsent(@Param("nameCsv") String nameCsv);

    /** Tag edits are replace-not-merge, so a removed tag actually disappears. */
    @Modifying
    @Query(value = "DELETE FROM problem_tags WHERE problem_id = :problemId", nativeQuery = true)
    void deleteTagsOfProblem(@Param("problemId") Long problemId);

    /** Attaches every named tag to the problem in one statement. */
    @Modifying
    @Query(value = """
            INSERT INTO problem_tags (problem_id, tag_id)
            SELECT :problemId, t.id
            FROM tags t
            WHERE t.name = ANY(string_to_array(:nameCsv, ','))
            ON CONFLICT DO NOTHING
            """, nativeQuery = true)
    void attachTags(@Param("problemId") Long problemId, @Param("nameCsv") String nameCsv);
}
