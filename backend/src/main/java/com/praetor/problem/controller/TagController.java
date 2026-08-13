package com.praetor.problem.controller;

import com.praetor.problem.service.ProblemReadService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Tag vocabulary (FR-14). Its own path rather than {@code /api/problems/tags}, which would sit under
 * the {@code /{slug}} mapping and read as a problem named "tags". Public, like the problem list —
 * the tag names carry no problem identity, so nothing here is affected by the contest embargo.
 */
@RestController
@RequestMapping("/api/tags")
public class TagController {

    private final ProblemReadService readService;

    public TagController(ProblemReadService readService) {
        this.readService = readService;
    }

    @GetMapping
    public List<String> list() {
        return readService.allTags();
    }
}
