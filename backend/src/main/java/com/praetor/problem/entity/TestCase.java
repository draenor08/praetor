package com.praetor.problem.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
        name = "test_cases",
        uniqueConstraints = @UniqueConstraint(
                columnNames = {"problem_id", "ord"}))
public class TestCase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "problem_id", nullable = false)
    private Long problemId;

    @Column(nullable = false)
    private Integer ord;

    @Column(nullable = false)
    private String kind = "HIDDEN";

    @Column(nullable = false, columnDefinition = "TEXT")
    private String input;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String expected;

    @Column(nullable = false)
    private Integer points = 0;

    protected TestCase() {
    }

    public TestCase(
            Long problemId,
            Integer ord,
            String kind,
            String input,
            String expected,
            Integer points) {

        this.problemId = problemId;
        this.ord = ord;
        this.kind = kind;
        this.input = input;
        this.expected = expected;
        this.points = points;
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
