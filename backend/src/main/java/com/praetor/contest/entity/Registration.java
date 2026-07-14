package com.praetor.contest.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.ZonedDateTime;

/**
 * A user's registration in a contest (maps {@code registrations}). {@code isVirtual}/{@code
 * virtualStart} support upsolving a past contest on your own clock (FR-22, deferred — writes reject
 * virtual for now).
 */
@Entity
@Table(name = "registrations")
public class Registration {

    @EmbeddedId
    private RegistrationId id;

    @Column(name = "is_virtual", nullable = false)
    private Boolean isVirtual;

    @Column(name = "virtual_start")
    private ZonedDateTime virtualStart;

    @Column(name = "registered_at", nullable = false)
    private ZonedDateTime registeredAt;

    protected Registration() {
    }

    public Registration(Long contestId, Long userId, boolean isVirtual) {
        this.id = new RegistrationId(contestId, userId);
        this.isVirtual = isVirtual;
    }

    @PrePersist
    void onCreate() {
        if (registeredAt == null) {
            registeredAt = ZonedDateTime.now();
        }
    }

    public RegistrationId getId() {
        return id;
    }

    public Boolean getIsVirtual() {
        return isVirtual;
    }

    public ZonedDateTime getVirtualStart() {
        return virtualStart;
    }

    public ZonedDateTime getRegisteredAt() {
        return registeredAt;
    }
}
