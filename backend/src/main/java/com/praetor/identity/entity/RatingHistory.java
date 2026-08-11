package com.praetor.identity.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.ZonedDateTime;

@Entity
@Table(name = "rating_history")
public class RatingHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "contest_id", nullable = false)
    private Long contestId;

    @Column(name = "rating_before", nullable = false)
    private Integer ratingBefore;

    @Column(name = "rating_after", nullable = false)
    private Integer ratingAfter;

    @Column(name = "created_at", nullable = false)
    private ZonedDateTime createdAt;

    protected RatingHistory() {
    }

    public RatingHistory(
            Long userId,
            Long contestId,
            Integer ratingBefore,
            Integer ratingAfter) {

        this.userId = userId;
        this.contestId = contestId;
        this.ratingBefore = ratingBefore;
        this.ratingAfter = ratingAfter;
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

    public Long getUserId() {
        return userId;
    }

    public Long getContestId() {
        return contestId;
    }

    public Integer getRatingBefore() {
        return ratingBefore;
    }

    public Integer getRatingAfter() {
        return ratingAfter;
    }

    public ZonedDateTime getCreatedAt() {
        return createdAt;
    }
}
