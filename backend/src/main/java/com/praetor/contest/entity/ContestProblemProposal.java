package com.praetor.contest.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.ZonedDateTime;

/**
 * A setter offering one of their draft problems for a contest. Accepting is what writes the
 * {@code contest_problems} row — this table records the offer and the decision, so a rejected
 * problem does not silently vanish from the setter's view.
 *
 * <p>{@code problemId} is a bare Long, not an association: the contest module reads the problem
 * module through projections, never through mapped entities.
 */
@Entity
@Table(name = "contest_problem_proposals")
public class ContestProblemProposal {

    /** Awaiting the admin's decision. */
    public static final String PROPOSED = "PROPOSED";

    /** Accepted — the problem is in the contest. */
    public static final String ACCEPTED = "ACCEPTED";

    /** Turned down; the problem stays a draft and can be offered to another contest. */
    public static final String REJECTED = "REJECTED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "contest_id", nullable = false)
    private Long contestId;

    @Column(name = "problem_id", nullable = false)
    private Long problemId;

    @Column(name = "proposed_by", nullable = false)
    private Long proposedBy;

    @Column(nullable = false, length = 20)
    private String status = PROPOSED;

    @Column(columnDefinition = "TEXT")
    private String note;

    @Column(name = "decided_at")
    private ZonedDateTime decidedAt;

    @Column(name = "created_at", nullable = false)
    private ZonedDateTime createdAt;

    protected ContestProblemProposal() {
    }

    public ContestProblemProposal(Long contestId, Long problemId, Long proposedBy, String note) {
        this.contestId = contestId;
        this.problemId = problemId;
        this.proposedBy = proposedBy;
        this.note = note;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = ZonedDateTime.now();
        }
    }

    public Long getId() {
        return id;
    }

    public Long getContestId() {
        return contestId;
    }

    public Long getProblemId() {
        return problemId;
    }

    public Long getProposedBy() {
        return proposedBy;
    }

    public String getStatus() {
        return status;
    }

    public String getNote() {
        return note;
    }

    public ZonedDateTime getDecidedAt() {
        return decidedAt;
    }

    public ZonedDateTime getCreatedAt() {
        return createdAt;
    }

    /** Record the admin's decision. */
    public void decide(String decision) {
        this.status = decision;
        this.decidedAt = ZonedDateTime.now();
    }
}
