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
 * A contest (maps {@code contests}). Sazid-canonical write entity. {@code freezeMin} = the last N
 * minutes whose results are hidden from non-privileged viewers; {@code scoring} is ICPC/POINTS
 * (only ICPC is rendered in the committed build).
 */
@Entity
@Table(name = "contests")
public class Contest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(name = "starts_at", nullable = false)
    private ZonedDateTime startsAt;

    @Column(name = "ends_at", nullable = false)
    private ZonedDateTime endsAt;

    @Column(name = "freeze_min", nullable = false)
    private Integer freezeMin;

    @Column(nullable = false)
    private String scoring;

    @Column(name = "created_at", nullable = false)
    private ZonedDateTime createdAt;

    protected Contest() {
    }

    public Contest(String title, ZonedDateTime startsAt, ZonedDateTime endsAt,
                   Integer freezeMin, String scoring) {
        this.title = title;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.freezeMin = freezeMin;
        this.scoring = scoring;
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

    public String getTitle() {
        return title;
    }

    public ZonedDateTime getStartsAt() {
        return startsAt;
    }

    public ZonedDateTime getEndsAt() {
        return endsAt;
    }

    public Integer getFreezeMin() {
        return freezeMin;
    }

    public String getScoring() {
        return scoring;
    }

    public ZonedDateTime getCreatedAt() {
        return createdAt;
    }
}
