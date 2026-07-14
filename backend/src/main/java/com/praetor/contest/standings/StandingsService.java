package com.praetor.contest.standings;

import com.praetor.contest.dto.ContestProblemDto;
import com.praetor.contest.dto.StandingsResponse;
import com.praetor.contest.entity.Contest;
import com.praetor.contest.repository.ContestProblemRepository;
import com.praetor.contest.repository.ContestRepository;
import com.praetor.contest.repository.RegistrationRepository;
import com.praetor.contest.repository.StandingsRepository;
import com.praetor.contest.repository.StandingsSubmissionRow;
import com.praetor.identity.entity.User;
import com.praetor.identity.repository.UserRepository;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Loads the data the {@link StandingsCalculator} folds into a board: the contest window, its
 * problems (display order), its registered participants (row set + handles), and every judged
 * submission. Reads only — the write side is {@link com.praetor.contest.service.ContestService}.
 */
@Service
public class StandingsService {

    private final ContestRepository contestRepo;
    private final ContestProblemRepository contestProblemRepo;
    private final RegistrationRepository registrationRepo;
    private final StandingsRepository standingsRepo;
    private final UserRepository userRepo;
    private final StandingsCalculator calculator;

    public StandingsService(ContestRepository contestRepo, ContestProblemRepository contestProblemRepo,
                            RegistrationRepository registrationRepo, StandingsRepository standingsRepo,
                            UserRepository userRepo, StandingsCalculator calculator) {
        this.contestRepo = contestRepo;
        this.contestProblemRepo = contestProblemRepo;
        this.registrationRepo = registrationRepo;
        this.standingsRepo = standingsRepo;
        this.userRepo = userRepo;
        this.calculator = calculator;
    }

    /**
     * Compute the current board for a contest.
     *
     * @param privileged true for ADMIN/PROBLEM_SETTER viewers (see through an active freeze).
     * @throws ResponseStatusException 404 if the contest doesn't exist.
     */
    @Transactional(readOnly = true)
    public StandingsResponse snapshot(Long contestId, boolean privileged) {
        Contest contest = contestRepo.findById(contestId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "contest not found"));

        List<ContestProblemDto> problems = contestProblemRepo
                .findByIdContestIdOrderByOrdAsc(contestId).stream()
                .map(cp -> new ContestProblemDto(cp.getLabel(), cp.getOrd(), cp.getProblemId()))
                .toList();

        List<Long> userIds = registrationRepo.findByIdContestId(contestId).stream()
                .map(r -> r.getId().getUserId())
                .toList();
        // userId → handle, ordered by handle for a stable board before ranking reorders it.
        LinkedHashMap<Long, String> participants = new LinkedHashMap<>();
        userRepo.findAllById(userIds).stream()
                .sorted(Comparator.comparing(User::getUsername))
                .forEach(u -> participants.put(u.getId(), u.getUsername()));

        List<StandingsSubmissionRow> subs = standingsRepo.findJudged(contestId);

        return calculator.compute(contestId,
                contest.getStartsAt().toInstant(), contest.getEndsAt().toInstant(),
                contest.getFreezeMin(), problems, participants, subs, privileged, Instant.now());
    }
}
