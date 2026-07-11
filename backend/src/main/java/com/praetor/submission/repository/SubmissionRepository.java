package com.praetor.submission.repository;

import com.praetor.submission.entity.Submission;
import java.time.ZonedDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SubmissionRepository extends JpaRepository<Submission, Long> {

    /** Rows stuck in a status past a cutoff — used by the reaper for crash recovery. */
    List<Submission> findByStatusAndCreatedAtBefore(String status, ZonedDateTime cutoff);
}
