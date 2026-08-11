package com.praetor.problem.repository;

import com.praetor.problem.entity.Problem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProblemRepository
        extends JpaRepository<Problem, Long> {

    Optional<Problem> findBySlug(String slug);

    boolean existsBySlug(String slug);

    List<Problem> findAllByOrderByDifficultyAscTitleAsc();
}
