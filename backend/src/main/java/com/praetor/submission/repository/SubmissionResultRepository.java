package com.praetor.submission.repository;

import com.praetor.submission.entity.SubmissionResult;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface SubmissionResultRepository extends JpaRepository<SubmissionResult, Long> {

    /**
     * Delete all per-testcase rows of a submission — used by rejudge (FR-27) before re-enqueue. The
     * re-judge re-inserts rows via {@code saveAll}, and {@code submission_results} has
     * {@code UNIQUE(submission_id, test_case_id)}, so the old rows MUST go first. Also clears the
     * stale practice-reveal {@code actual_output} (it lives on these rows, no separate store).
     */
    @Modifying
    @Query("delete from SubmissionResult r where r.submissionId = :submissionId")
    void deleteBySubmissionId(@Param("submissionId") Long submissionId);

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

    /**
     * The failing test case's input/expected/actual for the practice-mode reveal (feat 3d). The
     * judge loop breaks on the first non-AC, so at most one non-AC row exists; the {@code <> 'AC'}
     * filter + {@code findFirst} is defensive. CALL ONLY after the practice/non-contest gate passes
     * — this is the one query that surfaces hidden test-case input/expected to a user.
     */
    @Query("select tc.ord as ord, tc.input as input, tc.expected as expected, "
            + "r.actualOutput as actualOutput "
            + "from SubmissionResult r, JudgeTestCase tc "
            + "where tc.id = r.testCaseId and r.submissionId = :submissionId and r.verdict <> 'AC' "
            + "order by tc.ord")
    List<RevealView> findFailingReveal(@Param("submissionId") Long submissionId);
}
