package com.praetor.contest.service;

import com.praetor.contest.dto.AcceptProposalRequest;
import com.praetor.contest.dto.ProposalDto;
import com.praetor.contest.dto.ProposeRequest;
import com.praetor.contest.entity.Contest;
import com.praetor.contest.entity.ContestProblem;
import com.praetor.contest.entity.ContestProblemProposal;
import com.praetor.contest.repository.ContestAccessRepository;
import com.praetor.contest.repository.ContestProblemRepository;
import com.praetor.contest.repository.ContestRepository;
import com.praetor.contest.repository.ProposalRepository;
import com.praetor.contest.repository.ProposalRow;
import com.praetor.identity.entity.User;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Setters offer problems for a contest; the admin decides. Accepting is what actually puts the
 * problem in the contest, so the admin keeps control of the problem set and its labels.
 *
 * <p>Eligibility is re-checked at accept time, not only at propose time: a draft can be published
 * (or claimed by another contest) in between, and letting a published statement into a contest is
 * exactly what the rule exists to prevent.
 */
@Service
public class ProposalService {

    private final ProposalRepository proposalRepo;
    private final ContestRepository contestRepo;
    private final ContestProblemRepository contestProblemRepo;
    private final ContestAccessRepository accessRepo;

    public ProposalService(ProposalRepository proposalRepo, ContestRepository contestRepo,
                           ContestProblemRepository contestProblemRepo,
                           ContestAccessRepository accessRepo) {
        this.proposalRepo = proposalRepo;
        this.contestRepo = contestRepo;
        this.contestProblemRepo = contestProblemRepo;
        this.accessRepo = accessRepo;
    }

    /** A setter offers one of their drafts. Only while the contest is taking submissions. */
    @Transactional
    public ProposalDto propose(Long contestId, ProposeRequest req, User user) {
        requireStaff(user, "propose problems");

        Contest contest = contest(contestId);
        if (!contest.isCallsOpen()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "this contest is not accepting problem proposals");
        }
        if (proposalRepo.existsByContestIdAndProblemId(contestId, req.problemId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "this problem has already been proposed for this contest");
        }
        requireEligible(req.problemId());

        ContestProblemProposal saved = proposalRepo.save(
                new ContestProblemProposal(contestId, req.problemId(), user.getId(), req.note()));

        return find(contestId, saved.getId());
    }

    /**
     * The admin accepts: the problem joins the contest under the given label. Re-validates the
     * problem, and lets the unique constraints on {@code contest_problems} reject a duplicate label
     * rather than racing a lookup against it.
     */
    @Transactional
    public ProposalDto accept(Long contestId, Long proposalId, AcceptProposalRequest req, User user) {
        requireAdmin(user);

        ContestProblemProposal proposal = pending(contestId, proposalId);
        requireEligible(proposal.getProblemId());

        String label = req.label().trim().toUpperCase();
        if (contestProblemRepo.existsByIdContestIdAndLabel(contestId, label)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "label " + label + " is already taken in this contest");
        }

        int nextOrd = contestProblemRepo.findByIdContestIdOrderByOrdAsc(contestId).size() + 1;
        contestProblemRepo.save(
                new ContestProblem(contestId, proposal.getProblemId(), label, nextOrd));

        proposal.decide(ContestProblemProposal.ACCEPTED);
        proposalRepo.save(proposal);

        return find(contestId, proposalId);
    }

    /** The admin turns it down. The problem stays a draft and may be offered elsewhere. */
    @Transactional
    public ProposalDto reject(Long contestId, Long proposalId, User user) {
        requireAdmin(user);

        ContestProblemProposal proposal = pending(contestId, proposalId);
        proposal.decide(ContestProblemProposal.REJECTED);
        proposalRepo.save(proposal);

        return find(contestId, proposalId);
    }

    /** Everything proposed for a contest — the admin's review queue. */
    @Transactional(readOnly = true)
    public List<ProposalDto> listForContest(Long contestId, User user) {
        requireStaff(user, "view proposals");
        contest(contestId);
        return proposalRepo.findRowsByContestId(contestId).stream()
                .map(r -> toDto(contestId, r))
                .toList();
    }

    /** Everything one setter has offered, across contests. */
    @Transactional(readOnly = true)
    public List<ProposalDto> listMine(User user) {
        requireStaff(user, "view proposals");
        return proposalRepo.findRowsByProposer(user.getId()).stream()
                .map(r -> toDto(null, r))
                .toList();
    }

    private ContestProblemProposal pending(Long contestId, Long proposalId) {
        ContestProblemProposal proposal = proposalRepo.findById(proposalId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "proposal not found"));
        if (!proposal.getContestId().equals(contestId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "proposal not found");
        }
        if (!ContestProblemProposal.PROPOSED.equals(proposal.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "this proposal was already " + proposal.getStatus().toLowerCase());
        }
        return proposal;
    }

    private Contest contest(Long contestId) {
        return contestRepo.findById(contestId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "contest not found"));
    }

    private void requireEligible(Long problemId) {
        if (!accessRepo.isEligibleForContest(problemId)) {
            String reason = accessRepo.ineligibleReason(problemId);
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "problem cannot be used by a contest: "
                            + (reason == null ? "no such problem" : reason));
        }
    }

    private ProposalDto find(Long contestId, Long proposalId) {
        return proposalRepo.findRowsByContestId(contestId).stream()
                .filter(r -> r.getId().equals(proposalId))
                .findFirst()
                .map(r -> toDto(contestId, r))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "proposal not found"));
    }

    private ProposalDto toDto(Long contestId, ProposalRow r) {
        return new ProposalDto(r.getId(), contestId, r.getProblemId(), r.getSlug(), r.getTitle(),
                r.getDifficulty(), r.getJudgeMode(), r.getProposedBy(), r.getStatus(), r.getNote(),
                r.getTestCases() == null ? 0L : r.getTestCases(),
                r.getCreatedAt() == null ? null : r.getCreatedAt().toString());
    }

    private void requireAdmin(User user) {
        if (user == null || !"ADMIN".equals(user.getRole())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "only ADMIN may decide proposals");
        }
    }

    private void requireStaff(User user, String action) {
        if (user == null || "USER".equals(user.getRole())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "only staff may " + action);
        }
    }
}
