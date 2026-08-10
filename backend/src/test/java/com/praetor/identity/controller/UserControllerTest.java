package com.praetor.identity.controller;

import com.praetor.identity.dto.UserResponse;
import com.praetor.identity.entity.User;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UserControllerTest {

    @Test
    void getCurrentUserReturnsAuthenticatedUserProfile() {

        User user = new User();
        user.setId(1L);
        user.setFullName("Yeasir");
        user.setUsername("yeasir");
        user.setEmail("yeasir@example.com");
        user.setRole("USER");

        UserController controller = new UserController();

        UserResponse response = controller.getCurrentUser(user);

        assertEquals(1L, response.getId());
        assertEquals("Yeasir", response.getFullName());
        assertEquals("yeasir", response.getUsername());
        assertEquals("yeasir@example.com", response.getEmail());
        assertEquals("USER", response.getRole());
    }
}