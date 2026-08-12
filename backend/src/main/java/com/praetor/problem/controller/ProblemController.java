package com.praetor.problem.controller;

import com.praetor.identity.entity.User;
import com.praetor.problem.dto.ProblemDetail;
import com.praetor.problem.dto.ProblemRequest;
import com.praetor.problem.dto.ProblemResponse;
import com.praetor.problem.dto.ProblemSummary;
import com.praetor.problem.service.ProblemReadService;
import com.praetor.problem.service.ProblemService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/problems")
public class ProblemController {

    private final ProblemReadService readService;
    private final ProblemService problemService;

    public ProblemController(
            ProblemReadService readService,
            ProblemService problemService) {

        this.readService = readService;
        this.problemService = problemService;
    }

    /**
     * The problem list. Public (see {@link com.praetor.problem.config.ProblemWebSecurityConfig}) —
     * the JWT is optional there, so {@code user} is null for an anonymous reader and the contest
     * embargo applies.
     */
    @GetMapping
    public List<ProblemSummary> list(
            @AuthenticationPrincipal User user) {

        return readService.list(user);
    }

    /** One problem's statement. Public, but embargoed problems are refused — see the read service. */
    @GetMapping("/{slug}")
    public ProblemDetail get(
            @PathVariable String slug,
            @AuthenticationPrincipal User user) {

        return readService.get(slug, user);
    }

    @PostMapping
    public ResponseEntity<ProblemResponse> create(
            @RequestBody ProblemRequest request,
            @AuthenticationPrincipal User user) {

        ProblemResponse response =
                problemService.create(
                        request,
                        user);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    @PutMapping("/{slug}")
    public ProblemResponse update(
            @PathVariable String slug,
            @RequestBody ProblemRequest request,
            @AuthenticationPrincipal User user) {

        return problemService.update(
                slug,
                request,
                user);
    }

    @DeleteMapping("/{slug}")
    public ResponseEntity<Void> delete(
            @PathVariable String slug,
            @AuthenticationPrincipal User user) {

        problemService.delete(
                slug,
                user);

        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{slug}/archive")
    public ProblemResponse archive(
            @PathVariable String slug,
            @AuthenticationPrincipal User user) {

        return problemService.setArchived(
                slug,
                true,
                user);
    }

    @PostMapping("/{slug}/unarchive")
    public ProblemResponse unarchive(
            @PathVariable String slug,
            @AuthenticationPrincipal User user) {

        return problemService.setArchived(
                slug,
                false,
                user);
    }
}
