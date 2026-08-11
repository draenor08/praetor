package com.praetor.contest.service;

import com.praetor.contest.dto.ContestProblemDto;
import com.praetor.contest.dto.ContestResponse;
import com.praetor.contest.dto.ContestSummary;
import com.praetor.contest.dto.CreateContestRequest;
import com.praetor.contest.dto.RegisterRequest;
import com.praetor.contest.entity.Contest;
import com.praetor.contest.entity.ContestProblem;
import com.praetor.contest.entity.Registration;
import com.praetor.contest.entity.RegistrationId;
import com.praetor.contest.repository.ContestProblemRepository;
import com.praetor.contest.repository.ContestRepository;
import com.praetor.contest.repository.RegistrationRepository;
import com.praetor.identity.entity.User;
import com.praetor.identity.service.RatingService;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ContestService {

    private final ContestRepository contestRepo;
    private final ContestProblemRepository contestProblemRepo;
    private final RegistrationRepository registrationRepo;
    private final RatingService ratingService;

    public ContestService(
            ContestRepository contestRepo,
            ContestProblemRepository contestProblemRepo,
            RegistrationRepository registrationRepo,
            RatingService ratingService) {

        this.contestRepo = contestRepo;
        this.contestProblemRepo = contestProblemRepo;
        this.registrationRepo = registrationRepo;
        this.ratingService = ratingService;
    }

    @Transactional
    public ContestResponse create(
            CreateContestRequest req,
            User user) {

        if (!"ADMIN".equals(user.getRole())) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "only ADMIN may create contests");
        }

        if (!req.endsAt().isAfter(req.startsAt())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "endsAt must be after startsAt");
        }

        long distinctLabels = req.problems()
                .stream()
                .map(p -> p.label().trim())
                .distinct()
                .count();

        if (distinctLabels != req.problems().size()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "duplicate problem labels");
        }

        Contest contest = contestRepo.save(
                new Contest(
                        req.title(),
                        req.startsAt(),
                        req.endsAt(),
                        req.freezeMin(),
                        req.scoring()));

        List<ContestProblem> problems =
                req.problems()
                        .stream()
                        .map(p -> new ContestProblem(
                                contest.getId(),
                                p.problemId(),
                                p.label(),
                                p.ord()))
                        .toList();

        contestProblemRepo.saveAll(problems);

        return toResponse(contest);
    }

    @Transactional(readOnly = true)
    public List<ContestSummary> list() {

        return contestRepo.findAll()
                .stream()
                .map(c -> new ContestSummary(
                        c.getId(),
                        c.getTitle(),
                        c.getStartsAt()
                                .toInstant()
                                .toString(),
                        c.getEndsAt()
                                .toInstant()
                                .toString(),
                        c.getScoring()))
                .toList();
    }

    @Transactional(readOnly = true)
    public ContestResponse get(Long id) {

        Contest contest = contestRepo.findById(id)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "contest not found"));

        return toResponse(contest);
    }

    @Transactional
    public void register(
            Long contestId,
            RegisterRequest req,
            User user) {

        if (req.virtual()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "virtual registration not supported");
        }

        if (!contestRepo.existsById(contestId)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "contest not found");
        }

        if (registrationRepo.existsById(
                new RegistrationId(
                        contestId,
                        user.getId()))) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "already registered");
        }

        registrationRepo.save(
                new Registration(
                        contestId,
                        user.getId(),
                        false));
    }

    @Scheduled(fixedDelayString = "${praetor.rating.contest-scan-ms:60000}")
    @Transactional
    public void processEndedContests() {

        ZonedDateTime now =
                ZonedDateTime.now();

        contestRepo.findAll()
                .stream()
                .filter(contest ->
                        !contest.getEndsAt()
                                .isAfter(now))
                .forEach(contest ->
                        ratingService.applyContestResults(
                                contest.getId()));
    }

    private ContestResponse toResponse(
            Contest contest) {

        List<ContestProblemDto> problems =
                contestProblemRepo
                        .findByIdContestIdOrderByOrdAsc(
                                contest.getId())
                        .stream()
                        .map(cp ->
                                new ContestProblemDto(
                                        cp.getLabel(),
                                        cp.getOrd(),
                                        cp.getProblemId()))
                        .collect(Collectors.toList());

        return new ContestResponse(
                contest.getId(),
                contest.getTitle(),
                contest.getStartsAt()
                        .toInstant()
                        .toString(),
                contest.getEndsAt()
                        .toInstant()
                        .toString(),
                contest.getFreezeMin(),
                contest.getScoring(),
                problems);
    }
}
