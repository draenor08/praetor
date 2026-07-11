package com.praetor.submission.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * One test case's outcome for a submission. Maps {@code submission_results}
 * (UNIQUE(submission_id, test_case_id)). {@code verdict} is NOT NULL here (a result only exists
 * once its test case has been judged), unlike {@link Submission#getVerdict()} which is nullable
 * until the whole submission finishes.
 */
@Entity
@Table(name = "submission_results")
public class SubmissionResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "submission_id", nullable = false)
    private Long submissionId;

    @Column(name = "test_case_id", nullable = false)
    private Long testCaseId;

    @Column(nullable = false)
    private String verdict;

    @Column(name = "time_ms")
    private Integer timeMs;

    @Column(name = "mem_kb")
    private Integer memKb;

    protected SubmissionResult() {
    }

    public SubmissionResult(Long submissionId, Long testCaseId, String verdict,
                            Integer timeMs, Integer memKb) {
        this.submissionId = submissionId;
        this.testCaseId = testCaseId;
        this.verdict = verdict;
        this.timeMs = timeMs;
        this.memKb = memKb;
    }

    public Long getId() {
        return id;
    }

    public Long getSubmissionId() {
        return submissionId;
    }

    public Long getTestCaseId() {
        return testCaseId;
    }

    public String getVerdict() {
        return verdict;
    }

    public Integer getTimeMs() {
        return timeMs;
    }

    public Integer getMemKb() {
        return memKb;
    }
}
