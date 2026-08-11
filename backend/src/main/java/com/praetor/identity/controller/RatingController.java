package com.praetor.identity.controller;

import com.praetor.identity.dto.LeaderboardResponse;
import com.praetor.identity.dto.RatingResponse;
import com.praetor.identity.entity.User;
import com.praetor.identity.service.RatingService;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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

    /**
     * Applies a finished contest's ratings now instead of waiting for the scheduler's next
     * tick. ADMIN only, idempotent. Mounted here rather than under {@code /api/contests} so
     * the contest module's controller stays untouched.
     */
    @PostMapping("/ratings/apply/{contestId}")
    public ResponseEntity<Map<String, Object>> applyContestRatings(
            @PathVariable Long contestId,
            @AuthenticationPrincipal User user) {

        ratingService.applyContestResults(contestId, user);

        return ResponseEntity
                .accepted()
                .body(Map.of("contestId", contestId, "status", "APPLIED"));
    }
}
