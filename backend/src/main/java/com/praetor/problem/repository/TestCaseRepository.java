package com.praetor.problem.repository;

import com.praetor.problem.entity.TestCase;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TestCaseRepository
        extends JpaRepository<TestCase, Long> {

    List<TestCase> findByProblemIdOrderByOrdAsc(Long problemId);

    @Modifying
    @Query("delete from TestCase t where t.problemId = :problemId")
    void deleteByProblemId(@Param("problemId") Long problemId);
}
