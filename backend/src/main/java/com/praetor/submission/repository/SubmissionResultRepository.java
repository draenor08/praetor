package com.praetor.submission.repository;

import com.praetor.submission.entity.SubmissionResult;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface SubmissionResultRepository extends JpaRepository<SubmissionResult, Long> {

    /**
     * Per-test-case results for a submission, ordered by the test case's {@code ord}. Joins the
     * read-only {@code JudgeTestCase} view since {@code submission_results} stores only the
     * test_case_id, not the ordinal.
     */
    @Query("select tc.ord as ord, r.verdict as verdict, r.timeMs as timeMs, r.memKb as memKb "
            + "from SubmissionResult r, JudgeTestCase tc "
            + "where tc.id = r.testCaseId and r.submissionId = :submissionId "
            + "order by tc.ord")
    List<ResultView> findResultViews(@Param("submissionId") Long submissionId);
}
