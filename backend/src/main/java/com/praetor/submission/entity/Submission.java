package com.praetor.submission.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.ZonedDateTime;

/**
 * A code submission. Write entity owned by the submission slice. Maps the hand-written
 * {@code submissions} table (schema owned by db/schema.sql, ddl-auto=none — columns must match).
 *
 * <p>{@code status} (QUEUED→JUDGING→DONE|ERROR) = pipeline state; {@code verdict}
 * (AC/WA/TLE/MLE/RE/CE/PE, nullable until judged) = outcome. Enums are stored as Strings; the DB
 * CHECK constraints enforce the allowed sets.
 */
@Entity
@Table(name = "submissions")
public class Submission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "problem_id", nullable = false)
    private Long problemId;

    @Column(name = "contest_id")
    private Long contestId;

    @Column(nullable = false)
    private String language;

    @Column(name = "source_code", nullable = false)
    private String sourceCode;

    @Column(nullable = false)
    private String status;

    @Column
    private String verdict;

    @Column(name = "time_ms")
    private Integer timeMs;

    @Column(name = "mem_kb")
    private Integer memKb;

    @Column(name = "compile_log")
    private String compileLog;

    @Column(nullable = false)
    private Integer score;

    @Column(name = "created_at", nullable = false)
    private ZonedDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (status == null) {
            status = "QUEUED";
        }
        if (score == null) {
            score = 0;
        }
        if (createdAt == null) {
            createdAt = ZonedDateTime.now();
        }
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getProblemId() {
        return problemId;
    }

    public void setProblemId(Long problemId) {
        this.problemId = problemId;
    }

    public Long getContestId() {
        return contestId;
    }

    public void setContestId(Long contestId) {
        this.contestId = contestId;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public String getSourceCode() {
        return sourceCode;
    }

    public void setSourceCode(String sourceCode) {
        this.sourceCode = sourceCode;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getVerdict() {
        return verdict;
    }

    public void setVerdict(String verdict) {
        this.verdict = verdict;
    }

    public Integer getTimeMs() {
        return timeMs;
    }

    public void setTimeMs(Integer timeMs) {
        this.timeMs = timeMs;
    }

    public Integer getMemKb() {
        return memKb;
    }

    public void setMemKb(Integer memKb) {
        this.memKb = memKb;
    }

    public String getCompileLog() {
        return compileLog;
    }

    public void setCompileLog(String compileLog) {
        this.compileLog = compileLog;
    }

    public Integer getScore() {
        return score;
    }

    public void setScore(Integer score) {
        this.score = score;
    }

    public ZonedDateTime getCreatedAt() {
        return createdAt;
    }
}
