package com.praetor.problem.service;

import com.praetor.identity.entity.User;
import com.praetor.problem.dto.ProblemRequest;
import com.praetor.problem.dto.ProblemResponse;
import com.praetor.problem.entity.Problem;
import com.praetor.problem.repository.ProblemRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Set;

@Service
public class ProblemService {

    private static final Set<String> JUDGE_MODES =
            Set.of("EXACT", "TOKEN", "FLOAT", "SPECIAL");

    private final ProblemRepository problemRepository;

    public ProblemService(ProblemRepository problemRepository) {
        this.problemRepository = problemRepository;
    }

    @Transactional
    public ProblemResponse create(
            ProblemRequest request,
            User user) {

        requireProblemSetter(user);
        validate(request);

        String slug = normalizeSlug(request.slug());

        if (problemRepository.existsBySlug(slug)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "problem slug already exists");
        }

        Problem problem = new Problem(
                slug,
                request.title().trim(),
                request.statement(),
                request.constraints(),
                valueOrDefault(request.difficulty(), 800),
                valueOrDefault(request.timeLimitMs(), 1000),
                valueOrDefault(request.memLimitKb(), 262144),
                normalizeJudgeMode(request.judgeMode()),
                request.floatEps(),
                request.checkerCode(),
                request.editorial(),
                user.getId());

        return toResponse(
                problemRepository.save(problem));
    }

    @Transactional
    public ProblemResponse update(
            String currentSlug,
            ProblemRequest request,
            User user) {

        requireProblemSetter(user);
        validate(request);

        Problem problem =
                problemRepository
                        .findBySlug(currentSlug)
                        .orElseThrow(() ->
                                new ResponseStatusException(
                                        HttpStatus.NOT_FOUND,
                                        "problem not found"));

        String newSlug =
                normalizeSlug(request.slug());

        problemRepository
                .findBySlug(newSlug)
                .ifPresent(existing -> {
                    if (!existing.getId()
                            .equals(problem.getId())) {

                        throw new ResponseStatusException(
                                HttpStatus.CONFLICT,
                                "problem slug already exists");
                    }
                });

        problem.setSlug(newSlug);
        problem.setTitle(request.title().trim());
        problem.setStatement(request.statement());
        problem.setConstraints(request.constraints());
        problem.setDifficulty(
                valueOrDefault(
                        request.difficulty(),
                        800));
        problem.setTimeLimitMs(
                valueOrDefault(
                        request.timeLimitMs(),
                        1000));
        problem.setMemLimitKb(
                valueOrDefault(
                        request.memLimitKb(),
                        262144));
        problem.setJudgeMode(
                normalizeJudgeMode(
                        request.judgeMode()));
        problem.setFloatEps(request.floatEps());
        problem.setCheckerCode(
                request.checkerCode());
        problem.setEditorial(
                request.editorial());

        return toResponse(
                problemRepository.save(problem));
    }

    @Transactional
    public void delete(
            String slug,
            User user) {

        requireAdmin(user);

        Problem problem =
                problemRepository
                        .findBySlug(slug)
                        .orElseThrow(() ->
                                new ResponseStatusException(
                                        HttpStatus.NOT_FOUND,
                                        "problem not found"));

        problemRepository.delete(problem);
    }

    private void validate(ProblemRequest request) {

        if (request == null) {
            throw badRequest(
                    "request is required");
        }

        if (request.slug() == null
                || request.slug().isBlank()) {
            throw badRequest(
                    "slug is required");
        }

        String slug =
                normalizeSlug(request.slug());

        if (!slug.matches(
                "^[a-z0-9]+(?:-[a-z0-9]+)*$")) {

            throw badRequest(
                    "slug must contain lowercase letters, numbers and hyphens");
        }

        if (slug.length() > 80) {
            throw badRequest(
                    "slug must be at most 80 characters");
        }

        if (request.title() == null
                || request.title().isBlank()) {
            throw badRequest(
                    "title is required");
        }

        if (request.title().trim().length() > 200) {
            throw badRequest(
                    "title must be at most 200 characters");
        }

        if (request.statement() == null
                || request.statement().isBlank()) {
            throw badRequest(
                    "statement is required");
        }

        int difficulty =
                valueOrDefault(
                        request.difficulty(),
                        800);

        if (difficulty < 0
                || difficulty > 4000) {
            throw badRequest(
                    "difficulty must be between 0 and 4000");
        }

        int timeLimit =
                valueOrDefault(
                        request.timeLimitMs(),
                        1000);

        if (timeLimit < 1) {
            throw badRequest(
                    "timeLimitMs must be >= 1");
        }

        int memoryLimit =
                valueOrDefault(
                        request.memLimitKb(),
                        262144);

        if (memoryLimit < 1) {
            throw badRequest(
                    "memLimitKb must be >= 1");
        }

        String judgeMode =
                normalizeJudgeMode(
                        request.judgeMode());

        if (!JUDGE_MODES.contains(
                judgeMode)) {
            throw badRequest(
                    "judgeMode must be EXACT, TOKEN, FLOAT or SPECIAL");
        }

        if ("FLOAT".equals(judgeMode)
                && (request.floatEps() == null
                || request.floatEps() <= 0)) {

            throw badRequest(
                    "floatEps must be > 0 for FLOAT judge mode");
        }

        if ("SPECIAL".equals(judgeMode)
                && (request.checkerCode() == null
                || request.checkerCode().isBlank())) {

            throw badRequest(
                    "checkerCode is required for SPECIAL judge mode");
        }
    }

    private void requireProblemSetter(User user) {

        if (user == null
                || !"PROBLEM_SETTER".equals(
                        user.getRole())) {

            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "only PROBLEM_SETTER may create or update problems");
        }
    }

    private void requireAdmin(User user) {

        if (user == null
                || !"ADMIN".equals(
                        user.getRole())) {

            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "only ADMIN may delete problems");
        }
    }

    private String normalizeSlug(String slug) {
        return slug.trim().toLowerCase();
    }

    private String normalizeJudgeMode(
            String judgeMode) {

        if (judgeMode == null
                || judgeMode.isBlank()) {
            return "EXACT";
        }

        return judgeMode
                .trim()
                .toUpperCase();
    }

    private int valueOrDefault(
            Integer value,
            int defaultValue) {

        return value == null
                ? defaultValue
                : value;
    }

    private ResponseStatusException badRequest(
            String message) {

        return new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                message);
    }

    private ProblemResponse toResponse(
            Problem problem) {

        return new ProblemResponse(
                problem.getId(),
                problem.getSlug(),
                problem.getTitle(),
                problem.getStatement(),
                problem.getConstraints(),
                problem.getDifficulty(),
                problem.getTimeLimitMs(),
                problem.getMemLimitKb(),
                problem.getJudgeMode(),
                problem.getFloatEps(),
                problem.getCheckerCode(),
                problem.getEditorial(),
                problem.getCreatedBy());
    }
}
