package com.praetor.contest.service;

import com.praetor.contest.dto.ContestProblemSlot;
import com.praetor.contest.dto.ContestResponse;
import com.praetor.contest.dto.ContestSummary;
import com.praetor.contest.dto.CreateContestRequest;
import com.praetor.contest.dto.RegisterRequest;
import com.praetor.contest.entity.Contest;
import com.praetor.contest.entity.ContestProblem;
import com.praetor.contest.entity.Registration;
import com.praetor.contest.entity.RegistrationId;
import com.praetor.contest.repository.ContestProblemRepository;
import com.praetor.contest.repository.ContestProblemRow;
import com.praetor.contest.repository.ContestRepository;
import com.praetor.contest.repository.RegistrationRepository;
import com.praetor.identity.entity.User;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Contest CRUD + registration. ADMIN gating is in-service (the repo pattern in this codebase). */
@Service
public class ContestService {

    private final ContestRepository contestRepo;
    private final ContestProblemRepository contestProblemRepo;
    private final RegistrationRepository registrationRepo;
    private final ContestAccessService contestAccess;

    public ContestService(ContestRepository contestRepo, ContestProblemRepository contestProblemRepo,
                          RegistrationRepository registrationRepo, ContestAccessService contestAccess) {
        this.contestRepo = contestRepo;
        this.contestProblemRepo = contestProblemRepo;
        this.registrationRepo = registrationRepo;
        this.contestAccess = contestAccess;
    }

    @Transactional
    public ContestResponse create(CreateContestRequest req, User user) {
        if (!"ADMIN".equals(user.getRole())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "only ADMIN may create contests");
        }
        if (!req.endsAt().isAfter(req.startsAt())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "endsAt must be after startsAt");
        }
        long distinctLabels = req.problems().stream().map(p -> p.label().trim()).distinct().count();
        if (distinctLabels != req.problems().size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "duplicate problem labels");
        }

        Contest contest = contestRepo.save(new Contest(
                req.title(), req.startsAt(), req.endsAt(), req.freezeMin(), req.scoring()));
        List<ContestProblem> problems = req.problems().stream()
                .map(p -> new ContestProblem(contest.getId(), p.problemId(), p.label(), p.ord()))
                .toList();
        // Flushed, not merely saved: toResponse reads the slots back with a NATIVE query, which
        // Hibernate does not flush for. Without this the creator gets an empty problem list.
        contestProblemRepo.saveAllAndFlush(problems);
        return toResponse(contest, false, user);
    }

    @Transactional(readOnly = true)
    public List<ContestSummary> list() {
        return contestRepo.findAll().stream()
                .map(c -> new ContestSummary(c.getId(), c.getTitle(),
                        c.getStartsAt().toInstant().toString(),
                        c.getEndsAt().toInstant().toString(), c.getScoring()))
                .toList();
    }

    /**
     * Contest meta + problem slots. The slots keep their labels for everyone (the standings board
     * needs the columns), but only carry slug and title once the caller may open the statements:
     * staff always, a registered participant while the contest runs, anyone at all once it has
     * ended. See {@link ContestAccessService} — this reuses that rule rather than restating it.
     *
     * @param user the caller, or {@code null} for an anonymous reader
     */
    @Transactional(readOnly = true)
    public ContestResponse get(Long id, User user) {
        Contest contest = contestRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "contest not found"));
        boolean registered = user != null
                && registrationRepo.existsById(new RegistrationId(id, user.getId()));
        return toResponse(contest, registered, user);
    }

    @Transactional
    public void register(Long contestId, RegisterRequest req, User user) {
        if (req.virtual()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "virtual registration not supported");
        }
        if (!contestRepo.existsById(contestId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "contest not found");
        }
        if (registrationRepo.existsById(new RegistrationId(contestId, user.getId()))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "already registered");
        }
        registrationRepo.save(new Registration(contestId, user.getId(), false));
    }

    private ContestResponse toResponse(Contest contest, boolean registered, User user) {
        List<ContestProblemRow> rows = contestProblemRepo.findRowsByContestId(contest.getId());

        // One access question for the whole contest: its slots share a contest, so they share a
        // verdict. Asked against the first slot, and only when there is one.
        boolean visible = rows.isEmpty()
                || contestAccess.mayAccessProblem(rows.get(0).getProblemId(), user);

        List<ContestProblemSlot> problems = rows.stream()
                .map(r -> visible
                        ? new ContestProblemSlot(r.getLabel().trim(), r.getOrd(), r.getProblemId(),
                                r.getSlug(), r.getTitle())
                        : ContestProblemSlot.withheld(r.getLabel().trim(), r.getOrd(), r.getProblemId()))
                .collect(Collectors.toList());

        return new ContestResponse(
                contest.getId(), contest.getTitle(),
                contest.getStartsAt().toInstant().toString(),
                contest.getEndsAt().toInstant().toString(),
                contest.getFreezeMin(), contest.getScoring(), registered, visible, problems);
    }
}
