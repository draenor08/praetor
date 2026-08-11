package com.praetor.problem.controller;

import com.praetor.identity.entity.User;
import com.praetor.problem.dto.ManagedProblemResponse;
import com.praetor.problem.dto.ProblemResponse;
import com.praetor.problem.dto.ProblemUsageResponse;
import com.praetor.problem.service.ProblemService;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Staff-only reads for the problem workspace.
 *
 * <p>Deliberately mounted off {@code /api/problems}: {@code ProblemWebSecurityConfig} makes
 * every {@code GET /api/problems/*} anonymous, so a management read parked under that prefix
 * would arrive with no {@code Authentication} at all and could never be authorized. Under
 * {@code /api/setter/**} it falls through to the main authenticated chain, and the service
 * still re-checks the role.
 */
@RestController
@RequestMapping("/api/setter/problems")
public class SetterProblemController {

    private final ProblemService problemService;

    public SetterProblemController(ProblemService problemService) {
        this.problemService = problemService;
    }

    /** Every problem, archived ones included, each with the counts that gate its actions. */
    @GetMapping
    public List<ManagedProblemResponse> list(@AuthenticationPrincipal User user) {
        return problemService.listForManagement(user);
    }

    /** One problem in full — the editor form's load. */
    @GetMapping("/{slug}")
    public ProblemResponse get(@PathVariable String slug,
                               @AuthenticationPrincipal User user) {
        return problemService.getForManagement(slug, user);
    }

    /** Whether this problem can be hard-deleted, and if not, what is holding it. */
    @GetMapping("/{slug}/usage")
    public ProblemUsageResponse usage(@PathVariable String slug,
                                      @AuthenticationPrincipal User user) {
        return problemService.usage(slug, user);
    }
}
