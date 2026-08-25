package com.praetor.identity.controller;

import com.praetor.identity.dto.ProfileSolveStats;
import com.praetor.identity.service.ProfileService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class ProfileController {

    private final ProfileService profileService;

    public ProfileController(ProfileService profileService) {
        this.profileService = profileService;
    }

    @GetMapping("/{username}/stats")
    public ResponseEntity<ProfileSolveStats> getUserStats(@PathVariable("username") String username) {
        ProfileSolveStats stats = profileService.getSolveStats(username);
        return ResponseEntity.ok(stats);
    }
}
