package com.praetor.submission.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

/**
 * Read-only view of a problem, for the judging engine only. Maps {@code problems} but is
 * {@code @Immutable} and exposes only the columns the engine needs. A distinct name from any
 * future write-{@code Problem} entity (owned by the problem module) so the two coexist — this is
 * the insulation rule: the engine reads seed/DB data directly and survives broken teammate CRUD.
 */
@Entity
@Immutable
@Table(name = "problems")
public class JudgeProblem {

    @Id
    private Long id;

    @Column
    private String slug;

    @Column(name = "time_limit_ms")
    private Integer timeLimitMs;

    @Column(name = "mem_limit_kb")
    private Integer memLimitKb;

    @Column(name = "judge_mode")
    private String judgeMode;

    @Column(name = "float_eps")
    private Double floatEps;

    protected JudgeProblem() {
    }

    public Long getId() {
        return id;
    }

    public String getSlug() {
        return slug;
    }

    public Integer getTimeLimitMs() {
        return timeLimitMs;
    }

    public Integer getMemLimitKb() {
        return memLimitKb;
    }

    public String getJudgeMode() {
        return judgeMode;
    }

    public Double getFloatEps() {
        return floatEps;
    }
}
