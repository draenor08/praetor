package com.praetor.problem.controller;

import com.praetor.identity.entity.User;
import com.praetor.problem.dto.BulkTestCaseRequest;
import com.praetor.problem.dto.TestCaseResponse;
import com.praetor.problem.service.TestCaseService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/problems/{slug}/testcases")
public class TestCaseController {

    private final TestCaseService service;

    public TestCaseController(TestCaseService service) {
        this.service = service;
    }

    @GetMapping
    public List<TestCaseResponse> getTestCases(
            @PathVariable String slug,
            @AuthenticationPrincipal User user) {

        return service.getTestCases(slug, user);
    }

    @PostMapping("/bulk")
    public ResponseEntity<List<TestCaseResponse>> bulkUpdate(
            @PathVariable String slug,
            @RequestBody BulkTestCaseRequest request,
            @AuthenticationPrincipal User user) {

        List<TestCaseResponse> result =
                service.bulkUpdate(
                        slug,
                        request,
                        user);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(result);
    }
}
