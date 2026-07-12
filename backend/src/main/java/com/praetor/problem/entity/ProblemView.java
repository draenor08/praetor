package com.praetor.problem.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

/**
 * Read-only DISPLAY view of a problem, for the frontend problem list + statement page.
 *
 * <p>This is a W-shim: problem write-CRUD (the canonical {@code Problem} entity + controller) is the
 * problem module's responsibility (FR-12/13) and is not built yet, so submissions would have no
 * problem to open. This {@code @Immutable} projection reads the seeded {@code problems} rows directly
 * — same insulation rationale as the engine's {@code JudgeProblem}, but carrying the display columns
 * (title/statement/constraints/difficulty) that {@code JudgeProblem} deliberately omits.
 *
 * <p>Retire when the problem module ships: its {@code ProblemController} will map the same
 * {@code /api/problems} paths, so Spring will fail on duplicate mappings at boot — delete this shim
 * (entity, repo, service, controller) at that merge. Tracked as W-9 in the workaround register.
 */
@Entity
@Immutable
@Table(name = "problems")
public class ProblemView {

    @Id
    private Long id;

    @Column
    private String slug;

    @Column
    private String title;

    @Column
    private String statement;

    @Column(name = "constraints")
    private String constraints;

    @Column
    private Integer difficulty;

    @Column(name = "time_limit_ms")
    private Integer timeLimitMs;

    @Column(name = "mem_limit_kb")
    private Integer memLimitKb;

    @Column(name = "judge_mode")
    private String judgeMode;

    protected ProblemView() {
    }

    public Long getId() {
        return id;
    }

    public String getSlug() {
        return slug;
    }

    public String getTitle() {
        return title;
    }

    public String getStatement() {
        return statement;
    }

    public String getConstraints() {
        return constraints;
    }

    public Integer getDifficulty() {
        return difficulty;
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
}
