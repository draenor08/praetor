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
     * Creates the tag if this is the first problem to use it. {@code ON CONFLICT DO NOTHING} makes
     * the statement idempotent, so concurrent setters adding the same new tag cannot collide on the
     * unique index.
     */
    @Modifying
    @Query(value = "INSERT INTO tags (name) VALUES (:name) ON CONFLICT (name) DO NOTHING",
            nativeQuery = true)
    void insertTagIfAbsent(@Param("name") String name);

    @Query(value = "SELECT id FROM tags WHERE name = :name", nativeQuery = true)
    Long findTagIdByName(@Param("name") String name);

    /** Tag edits are replace-not-merge, so a removed tag actually disappears. */
    @Modifying
    @Query(value = "DELETE FROM problem_tags WHERE problem_id = :problemId", nativeQuery = true)
    void deleteTagsOfProblem(@Param("problemId") Long problemId);

    @Modifying
    @Query(value = """
            INSERT INTO problem_tags (problem_id, tag_id) VALUES (:problemId, :tagId)
            ON CONFLICT DO NOTHING
            """, nativeQuery = true)
    void insertProblemTag(@Param("problemId") Long problemId, @Param("tagId") Long tagId);
}
