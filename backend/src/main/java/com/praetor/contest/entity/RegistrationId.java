package com.praetor.contest.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;

/** Composite key for {@link Registration} = (contest_id, user_id). */
@Embeddable
public class RegistrationId implements Serializable {

    @Column(name = "contest_id")
    private Long contestId;

    @Column(name = "user_id")
    private Long userId;

    protected RegistrationId() {
    }

    public RegistrationId(Long contestId, Long userId) {
        this.contestId = contestId;
        this.userId = userId;
    }

    public Long getContestId() {
        return contestId;
    }

    public Long getUserId() {
        return userId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof RegistrationId other)) {
            return false;
        }
        return Objects.equals(contestId, other.contestId) && Objects.equals(userId, other.userId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(contestId, userId);
    }
}
