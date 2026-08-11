package com.praetor.identity.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "ratings")
public class Rating {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "value", nullable = false)
    private Integer value = 1500;

    protected Rating() {
    }

    public Rating(Long userId) {
        this.userId = userId;
        this.value = 1500;
    }

    public Rating(Long userId, Integer value) {
        this.userId = userId;
        this.value = value;
    }

    public Long getUserId() {
        return userId;
    }

    public Integer getValue() {
        return value;
    }

    public void setValue(Integer value) {
        this.value = value;
    }
}
