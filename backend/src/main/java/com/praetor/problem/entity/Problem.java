package com.praetor.problem.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.ZonedDateTime;

@Entity
@Table(name = "problems")
public class Problem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 80)
    private String slug;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String statement;

    @Column(columnDefinition = "TEXT")
    private String constraints;

    @Column(nullable = false)
    private Integer difficulty = 800;

    @Column(name = "time_limit_ms", nullable = false)
    private Integer timeLimitMs = 1000;

    @Column(name = "mem_limit_kb", nullable = false)
    private Integer memLimitKb = 262144;

    @Column(name = "judge_mode", nullable = false)
    private String judgeMode = "EXACT";

    @Column(name = "float_eps")
    private Double floatEps;

    @Column(name = "checker_code", columnDefinition = "TEXT")
    private String checkerCode;

    @Column(columnDefinition = "TEXT")
    private String editorial;

    @Column(name = "created_by")
    private Long createdBy;

    /** Retired from the public problem list, but every submission and standing it backs stays intact. */
    @Column(nullable = false)
    private boolean archived = false;

    @Column(name = "created_at", nullable = false)
    private ZonedDateTime createdAt;

    protected Problem() {
    }

    public Problem(
            String slug,
            String title,
            String statement,
            String constraints,
            Integer difficulty,
            Integer timeLimitMs,
            Integer memLimitKb,
            String judgeMode,
            Double floatEps,
            String checkerCode,
            String editorial,
            Long createdBy) {

        this.slug = slug;
        this.title = title;
        this.statement = statement;
        this.constraints = constraints;
        this.difficulty = difficulty;
        this.timeLimitMs = timeLimitMs;
        this.memLimitKb = memLimitKb;
        this.judgeMode = judgeMode;
        this.floatEps = floatEps;
        this.checkerCode = checkerCode;
        this.editorial = editorial;
        this.createdBy = createdBy;
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = ZonedDateTime.now();
        }
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

    public Double getFloatEps() {
        return floatEps;
    }

    public String getCheckerCode() {
        return checkerCode;
    }

    public String getEditorial() {
        return editorial;
    }

    public Long getCreatedBy() {
        return createdBy;
    }

    public ZonedDateTime getCreatedAt() {
        return createdAt;
    }

    public boolean isArchived() {
        return archived;
    }

    public void setArchived(boolean archived) {
        this.archived = archived;
    }

    public void setSlug(String slug) {
        this.slug = slug;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public void setStatement(String statement) {
        this.statement = statement;
    }

    public void setConstraints(String constraints) {
        this.constraints = constraints;
    }

    public void setDifficulty(Integer difficulty) {
        this.difficulty = difficulty;
    }

    public void setTimeLimitMs(Integer timeLimitMs) {
        this.timeLimitMs = timeLimitMs;
    }

    public void setMemLimitKb(Integer memLimitKb) {
        this.memLimitKb = memLimitKb;
    }

    public void setJudgeMode(String judgeMode) {
        this.judgeMode = judgeMode;
    }

    public void setFloatEps(Double floatEps) {
        this.floatEps = floatEps;
    }

    public void setCheckerCode(String checkerCode) {
        this.checkerCode = checkerCode;
    }

    public void setEditorial(String editorial) {
        this.editorial = editorial;
    }
}
