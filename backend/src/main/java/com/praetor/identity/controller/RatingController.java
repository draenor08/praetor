package com.praetor.identity.controller;

import com.praetor.identity.dto.LeaderboardResponse;
import com.praetor.identity.dto.RatingResponse;
import com.praetor.identity.service.RatingService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class RatingController {

    private final RatingService ratingService;

    public RatingController(RatingService ratingService) {
        this.ratingService = ratingService;
    }

    @GetMapping("/users/{handle}/rating")
    public RatingResponse getUserRating(
            @PathVariable String handle) {

        return ratingService.getUserRating(handle);
    }

    @GetMapping("/leaderboard")
    public LeaderboardResponse getLeaderboard(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        return ratingService.getLeaderboard(page, size);
    }
}
