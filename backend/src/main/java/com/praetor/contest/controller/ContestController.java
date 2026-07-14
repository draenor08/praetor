package com.praetor.contest.controller;

import com.praetor.contest.dto.ContestResponse;
import com.praetor.contest.dto.ContestSummary;
import com.praetor.contest.dto.CreateContestRequest;
import com.praetor.contest.dto.RegisterRequest;
import com.praetor.contest.dto.StandingsResponse;
import com.praetor.contest.service.ContestService;
import com.praetor.contest.standings.StandingsService;
import com.praetor.identity.entity.User;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/contests")
public class ContestController {

    private final ContestService service;
    private final StandingsService standingsService;

    public ContestController(ContestService service, StandingsService standingsService) {
        this.service = service;
        this.standingsService = standingsService;
    }

    /** Create a contest — ADMIN only (gated in-service → 403). */
    @PostMapping
    public ResponseEntity<ContestResponse> create(@Valid @RequestBody CreateContestRequest req,
                                                  @AuthenticationPrincipal User user) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(req, user));
    }

    /** All contests (meta only). Public (see ContestWebSecurityConfig). */
    @GetMapping
    public List<ContestSummary> list() {
        return service.list();
    }

    /** Contest meta + problem labels. Public (see ContestWebSecurityConfig). */
    @GetMapping("/{id}")
    public ContestResponse get(@PathVariable Long id) {
        return service.get(id);
    }

    /** Register the caller for a contest (USER). */
    @PostMapping("/{id}/register")
    public ResponseEntity<Void> register(@PathVariable Long id, @Valid @RequestBody RegisterRequest req,
                                         @AuthenticationPrincipal User user) {
        service.register(id, req, user);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    /**
     * ICPC standings snapshot. Public (see {@link com.praetor.contest.config.ContestWebSecurityConfig}) —
     * the JWT is optional there, so {@code user} is null for an anonymous spectator. Role-aware:
     * ADMIN/PROBLEM_SETTER see through an active freeze (live board); everyone else sees the frozen
     * board during a freeze window.
     */
    @GetMapping("/{id}/standings")
    public StandingsResponse standings(@PathVariable Long id, @AuthenticationPrincipal User user) {
        boolean privileged = user != null && !"USER".equals(user.getRole());
        return standingsService.snapshot(id, privileged);
    }
}
