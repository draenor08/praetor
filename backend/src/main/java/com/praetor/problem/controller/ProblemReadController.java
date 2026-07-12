package com.praetor.problem.controller;

import com.praetor.problem.dto.ProblemDetail;
import com.praetor.problem.dto.ProblemSummary;
import com.praetor.problem.service.ProblemReadService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-only problem endpoints backing the frontend problem list + statement page.
 *
 * <p>W-shim (see {@link com.praetor.problem.entity.ProblemView}). When the problem module ships its
 * own {@code ProblemController} on {@code /api/problems}, Spring will refuse to start on the
 * duplicate mappings — that boot failure is the signal to delete this shim.
 */
@RestController
@RequestMapping("/api/problems")
public class ProblemReadController {

    private final ProblemReadService service;

    public ProblemReadController(ProblemReadService service) {
        this.service = service;
    }

    @GetMapping
    public List<ProblemSummary> list() {
        return service.list();
    }

    @GetMapping("/{slug}")
    public ProblemDetail get(@PathVariable String slug) {
        return service.get(slug);
    }
}
