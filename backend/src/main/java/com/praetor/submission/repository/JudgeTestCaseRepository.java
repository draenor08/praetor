package com.praetor.submission.repository;

import com.praetor.submission.entity.JudgeTestCase;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface JudgeTestCaseRepository extends JpaRepository<JudgeTestCase, Long> {

    List<JudgeTestCase> findByProblemIdOrderByOrdAsc(Long problemId);
}
