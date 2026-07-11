package com.praetor.submission.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

/**
 * Read-only view of a test case, for the judging engine only. Maps {@code test_cases}
 * ({@code @Immutable}). Same insulation rationale as {@link JudgeProblem}.
 */
@Entity
@Immutable
@Table(name = "test_cases")
public class JudgeTestCase {

    @Id
    private Long id;

    @Column(name = "problem_id")
    private Long problemId;

    @Column(name = "ord")
    private Integer ord;

    @Column
    private String kind;

    @Column
    private String input;

    @Column
    private String expected;

    @Column
    private Integer points;

    protected JudgeTestCase() {
    }

    public Long getId() {
        return id;
    }

    public Long getProblemId() {
        return problemId;
    }

    public Integer getOrd() {
        return ord;
    }

    public String getKind() {
        return kind;
    }

    public String getInput() {
        return input;
    }

    public String getExpected() {
        return expected;
    }

    public Integer getPoints() {
        return points;
    }
}
