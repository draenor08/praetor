package com.praetor.submission.repository;

import com.praetor.submission.entity.JudgeProblem;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface JudgeProblemRepository extends JpaRepository<JudgeProblem, Long> {

    Optional<JudgeProblem> findBySlug(String slug);
}
