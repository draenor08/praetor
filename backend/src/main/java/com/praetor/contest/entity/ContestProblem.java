package com.praetor.contest.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * A problem slotted into a contest (maps {@code contest_problems}). {@code label} is CHAR(2) in the
 * DB (space-padded → {@link #getLabel()} trims). {@code problemId} is stored as a plain Long — NOT a
 * {@code @ManyToOne} to a write-{@code Problem} — to keep the contest module insulated from the
 * problem module.
 */
@Entity
@Table(name = "contest_problems")
public class ContestProblem {

    @EmbeddedId
    private ContestProblemId id;

    @Column(nullable = false)
    private String label;

    @Column(nullable = false)
    private Integer ord;

    protected ContestProblem() {
    }

    public ContestProblem(Long contestId, Long problemId, String label, Integer ord) {
        this.id = new ContestProblemId(contestId, problemId);
        this.label = label;
        this.ord = ord;
    }

    public ContestProblemId getId() {
        return id;
    }

    public Long getProblemId() {
        return id.getProblemId();
    }

    /** CHAR(2) comes back space-padded from Postgres — trim for the DTO/contract. */
    public String getLabel() {
        return label == null ? null : label.trim();
    }

    public Integer getOrd() {
        return ord;
    }
}
