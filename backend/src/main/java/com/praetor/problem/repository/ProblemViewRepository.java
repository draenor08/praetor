package com.praetor.problem.repository;

import com.praetor.problem.entity.ProblemView;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** Read-only access for the problem-read shim. See {@link ProblemView}. */
@Repository
public interface ProblemViewRepository extends JpaRepository<ProblemView, Long> {

    /** Public list: archived problems are retired from it (their detail page still resolves). */
    List<ProblemView> findAllByArchivedFalseOrderByDifficultyAscTitleAsc();

    Optional<ProblemView> findBySlug(String slug);
}
