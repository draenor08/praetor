package com.praetor.identity.controller;

import com.praetor.identity.dto.CurrentUserResponse;
import com.praetor.identity.entity.Rating;
import com.praetor.identity.entity.User;
import com.praetor.identity.repository.RatingRepository;
import com.praetor.identity.service.RatingService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final RatingRepository ratingRepository;

    public UserController(
            RatingRepository ratingRepository) {
        this.ratingRepository = ratingRepository;
    }

    @GetMapping("/me")
    public CurrentUserResponse getCurrentUser(
            @AuthenticationPrincipal User user) {

        int rating =
                ratingRepository
                        .findById(user.getId())
                        .map(Rating::getValue)
                        .orElse(
                                RatingService.DEFAULT_RATING);

        return new CurrentUserResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getRole(),
                rating);
    }
}
