package com.praetor.contest.repository;

import com.praetor.contest.entity.ContestProblem;
import com.praetor.contest.entity.ContestProblemId;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ContestProblemRepository extends JpaRepository<ContestProblem, ContestProblemId> {

    /** Problems of a contest in display order. Property path `id.contestId` through the @EmbeddedId. */
    List<ContestProblem> findByIdContestIdOrderByOrdAsc(Long contestId);
}
