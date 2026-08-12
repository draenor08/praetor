package com.praetor.contest.controller;

import com.praetor.contest.dto.AcceptProposalRequest;
import com.praetor.contest.dto.CallsOpenRequest;
import com.praetor.contest.dto.ContestResponse;
import com.praetor.contest.dto.ContestSummary;
import com.praetor.contest.dto.CreateContestRequest;
import com.praetor.contest.dto.EligibleProblemDto;
import com.praetor.contest.dto.ProposalDto;
import com.praetor.contest.dto.ProposeRequest;
import com.praetor.contest.dto.RegisterRequest;
import com.praetor.contest.dto.StandingsResponse;
import com.praetor.contest.service.ContestAccessService;
import com.praetor.contest.service.ContestService;
import com.praetor.contest.service.ProposalService;
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
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/contests")
public class ContestController {

    private final ContestService service;
    private final StandingsService standingsService;
    private final ProposalService proposals;
    private final ContestAccessService access;

    public ContestController(ContestService service, StandingsService standingsService,
                             ProposalService proposals, ContestAccessService access) {
        this.service = service;
        this.standingsService = standingsService;
        this.proposals = proposals;
        this.access = access;
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

    /**
     * Contest meta + problem slots. Public (see ContestWebSecurityConfig) — the JWT is optional
     * there, so {@code user} is null for an anonymous spectator, who gets labels without slugs.
     */
    @GetMapping("/{id}")
    public ContestResponse get(@PathVariable Long id, @AuthenticationPrincipal User user) {
        return service.get(id, user);
    }

    /**
     * Draft problems a contest may still use. Staff only — gated in-service, like everything else
     * in this module, since the GET chain here is permitAll with an optional JWT.
     */
    @GetMapping("/eligible-problems")
    public List<EligibleProblemDto> eligibleProblems(@AuthenticationPrincipal User user) {
        if (!access.isStaff(user)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "only staff may view the problem pool");
        }
        return access.eligiblePool();
    }

    /** Open or close this contest to setter proposals — ADMIN. */
    @PostMapping("/{id}/calls")
    public ContestResponse setCalls(@PathVariable Long id, @Valid @RequestBody CallsOpenRequest req,
                                    @AuthenticationPrincipal User user) {
        return service.setCallsOpen(id, req.open(), user);
    }

    /** A setter offers one of their drafts for this contest. */
    @PostMapping("/{id}/proposals")
    public ResponseEntity<ProposalDto> propose(@PathVariable Long id, @Valid @RequestBody ProposeRequest req,
                                               @AuthenticationPrincipal User user) {
        return ResponseEntity.status(HttpStatus.CREATED).body(proposals.propose(id, req, user));
    }

    /** This contest's proposals — the admin's review queue (staff may read). */
    @GetMapping("/{id}/proposals")
    public List<ProposalDto> listProposals(@PathVariable Long id, @AuthenticationPrincipal User user) {
        return proposals.listForContest(id, user);
    }

    /** Accept a proposal, putting the problem in the contest under a label — ADMIN. */
    @PostMapping("/{id}/proposals/{proposalId}/accept")
    public ProposalDto accept(@PathVariable Long id, @PathVariable Long proposalId,
                              @Valid @RequestBody AcceptProposalRequest req,
                              @AuthenticationPrincipal User user) {
        return proposals.accept(id, proposalId, req, user);
    }

    /** Turn a proposal down — ADMIN. The problem stays a draft and can go elsewhere. */
    @PostMapping("/{id}/proposals/{proposalId}/reject")
    public ProposalDto reject(@PathVariable Long id, @PathVariable Long proposalId,
                              @AuthenticationPrincipal User user) {
        return proposals.reject(id, proposalId, user);
    }

    /** Everything the calling setter has offered, across contests. */
    @GetMapping("/my-proposals")
    public List<ProposalDto> myProposals(@AuthenticationPrincipal User user) {
        return proposals.listMine(user);
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
