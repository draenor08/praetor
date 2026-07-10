package com.praetor.identity.dto;

import com.praetor.identity.entity.User;
import java.time.ZonedDateTime;

public class UserResponse {
    private Long id;
    private String fullName;
    private String username;
    private String email;
    private String role;
    private ZonedDateTime createdAt;
    private ZonedDateTime updatedAt;

    public UserResponse(User user) {
        this.id = user.getId();
        this.fullName = user.getFullName();
        this.username = user.getUsername();
        this.email = user.getEmail();
        this.role = user.getRole();
        this.createdAt = user.getCreatedAt();
        this.updatedAt = user.getUpdatedAt();
    }

    public Long getId() { return id; }
    public String getFullName() { return fullName; }
    public String getUsername() { return username; }
    public String getEmail() { return email; }
    public String getRole() { return role; }
    public ZonedDateTime getCreatedAt() { return createdAt; }
    public ZonedDateTime getUpdatedAt() { return updatedAt; }
}
