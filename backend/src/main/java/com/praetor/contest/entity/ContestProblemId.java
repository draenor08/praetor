package com.praetor.contest.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;

/** Composite key for {@link ContestProblem} = (contest_id, problem_id). */
@Embeddable
public class ContestProblemId implements Serializable {

    @Column(name = "contest_id")
    private Long contestId;

    @Column(name = "problem_id")
    private Long problemId;

    protected ContestProblemId() {
    }

    public ContestProblemId(Long contestId, Long problemId) {
        this.contestId = contestId;
        this.problemId = problemId;
    }

    public Long getContestId() {
        return contestId;
    }

    public Long getProblemId() {
        return problemId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ContestProblemId other)) {
            return false;
        }
        return Objects.equals(contestId, other.contestId) && Objects.equals(problemId, other.problemId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(contestId, problemId);
    }
}
