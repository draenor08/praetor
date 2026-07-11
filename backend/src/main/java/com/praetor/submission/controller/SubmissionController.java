package com.praetor.submission.controller;

import com.praetor.identity.entity.User;
import com.praetor.submission.dto.SubmissionCreatedResponse;
import com.praetor.submission.dto.SubmissionResponse;
import com.praetor.submission.dto.SubmitRequest;
import com.praetor.submission.service.SubmissionService;
import jakarta.validation.Valid;
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
@RequestMapping("/api/submissions")
public class SubmissionController {

    private final SubmissionService service;

    public SubmissionController(SubmissionService service) {
        this.service = service;
    }

    /** Submit code — 202 Accepted, judged asynchronously. */
    @PostMapping
    public ResponseEntity<SubmissionCreatedResponse> submit(@Valid @RequestBody SubmitRequest req,
                                                            @AuthenticationPrincipal User user) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(service.create(req, user));
    }

    /** Full submission incl. per-testcase results. Owner or ADMIN only (404 otherwise). */
    @GetMapping("/{id}")
    public SubmissionResponse get(@PathVariable Long id, @AuthenticationPrincipal User user) {
        return service.get(id, user);
    }
}
