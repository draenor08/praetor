package com.praetor.identity.controller;

import com.praetor.identity.dto.CurrentUserResponse;
import com.praetor.identity.entity.Rating;
import com.praetor.identity.entity.User;
import com.praetor.identity.repository.RatingRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UserControllerTest {

    @Test
    void getCurrentUserReturnsContractProfile() {

        RatingRepository ratingRepository =
                mock(RatingRepository.class);

        User user = new User();
        user.setId(1L);
        user.setUsername("yeasir");
        user.setFullName("Yeasir Anjir");
        user.setEmail("yeasir@example.com");
        user.setRole("USER");

        when(ratingRepository.findById(1L))
                .thenReturn(
                        Optional.of(
                                new Rating(
                                        1L,
                                        1675)));

        UserController controller =
                new UserController(
                        ratingRepository);

        CurrentUserResponse response =
                controller.getCurrentUser(user);

        assertEquals(1L, response.id());
        // Must match UserResponse.username — the frontend caches both under one key.
        assertEquals("yeasir", response.username());
        assertEquals("Yeasir Anjir", response.fullName());
        assertEquals(
                "yeasir@example.com",
                response.email());
        assertEquals("USER", response.role());
        assertEquals(1675, response.rating());
    }
}
