package com.praetor.problem.service;

import com.praetor.identity.entity.User;
import com.praetor.problem.dto.ManagedProblemResponse;
import com.praetor.problem.dto.ProblemRequest;
import com.praetor.problem.dto.ProblemResponse;
import com.praetor.problem.dto.ProblemUsageResponse;
import com.praetor.problem.entity.Problem;
import com.praetor.problem.repository.ProblemRepository;
import com.praetor.problem.repository.ProblemUsageRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Objects;
import java.util.Set;

@Service
public class ProblemService {

    private static final Set<String> JUDGE_MODES =
            Set.of("EXACT", "TOKEN", "FLOAT", "SPECIAL");

    private final ProblemRepository problemRepository;
    private final ProblemUsageRepository usageRepository;

    public ProblemService(
            ProblemRepository problemRepository,
            ProblemUsageRepository usageRepository) {

        this.problemRepository = problemRepository;
        this.usageRepository = usageRepository;
    }

    /**
     * Every problem including archived ones, for the setter workspace. The public list
     * ({@code ProblemReadService.list}) hides archived problems; this one must not, or a
     * setter could never find one to restore.
     */
    @Transactional(readOnly = true)
    public List<ManagedProblemResponse> listForManagement(User user) {

        ProblemAuthz.requireStaff(user, "manage problems");

        return usageRepository.findManagementRows()
                .stream()
                .map(row -> {
                    String lockReason = lockReason(
                            row.getContests(),
                            row.getSubmissions(),
                            row.getClarifications(),
                            null);

                    return new ManagedProblemResponse(
                            row.getSlug(),
                            row.getTitle(),
                            row.getDifficulty(),
                            row.getJudgeMode(),
                            row.getArchived(),
                            row.getTestCases(),
                            row.getSubmissions(),
                            row.getContests(),
                            row.getInLiveContest(),
                            lockReason == null,
                            lockReason);
                })
                .toList();
    }

    /** One problem in full (limits, checker, editorial, archived) — what the editor form loads. */
    @Transactional(readOnly = true)
    public ProblemResponse getForManagement(String slug, User user) {

        ProblemAuthz.requireStaff(user, "manage problems");

        return toResponse(
                problemRepository
                        .findBySlug(slug)
                        .orElseThrow(() ->
                                new ResponseStatusException(
                                        HttpStatus.NOT_FOUND,
                                        "problem not found")));
    }

    @Transactional
    public ProblemResponse create(
            ProblemRequest request,
            User user) {

        ProblemAuthz.requireStaff(user, "create problems");
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

        ProblemAuthz.requireStaff(user, "update problems");
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

        requireEditableNow(problem, request, newSlug);

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

    /**
     * Hard-deletes a problem, but only while nothing references it.
     *
     * <p>{@code test_cases} and {@code problem_tags} cascade, but {@code submissions},
     * {@code contest_problems} and {@code clarifications} are RESTRICT — so deleting a used
     * problem would fail in the database and surface as a 500. Worse, if those FKs ever
     * became cascades it would silently erase standings and rating history. Used problems
     * are archived instead, which is what the 409 body tells the caller to do.
     */
    @Transactional
    public void delete(
            String slug,
            User user) {

        ProblemAuthz.requireStaff(user, "delete problems");

        Problem problem =
                problemRepository
                        .findBySlug(slug)
                        .orElseThrow(() ->
                                new ResponseStatusException(
                                        HttpStatus.NOT_FOUND,
                                        "problem not found"));

        String blocker = deleteBlocker(problem.getId());

        if (blocker != null) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    blocker + " — archive it instead of deleting");
        }

        problemRepository.delete(problem);
    }

    /** Archives (hides from the public list) or restores a problem. */
    @Transactional
    public ProblemResponse setArchived(
            String slug,
            boolean archived,
            User user) {

        ProblemAuthz.requireStaff(user, "archive problems");

        Problem problem =
                problemRepository
                        .findBySlug(slug)
                        .orElseThrow(() ->
                                new ResponseStatusException(
                                        HttpStatus.NOT_FOUND,
                                        "problem not found"));

        problem.setArchived(archived);

        return toResponse(
                problemRepository.save(problem));
    }

    /** What references this problem, so the UI can offer Delete or Archive, never a dead 409. */
    @Transactional(readOnly = true)
    public ProblemUsageResponse usage(
            String slug,
            User user) {

        ProblemAuthz.requireStaff(user, "inspect problems");

        Problem problem =
                problemRepository
                        .findBySlug(slug)
                        .orElseThrow(() ->
                                new ResponseStatusException(
                                        HttpStatus.NOT_FOUND,
                                        "problem not found"));

        Long id = problem.getId();
        String blocker = deleteBlocker(id);

        return new ProblemUsageResponse(
                problem.getSlug(),
                blocker == null,
                problem.isArchived(),
                usageRepository.existsLiveContestForProblem(id),
                usageRepository.countSubmissions(id),
                usageRepository.countClarifications(id),
                usageRepository.findUsingContestTitle(id).orElse(null),
                blocker);
    }

    /** Human-readable reason this problem may not be hard-deleted, or null when it may. */
    private String deleteBlocker(Long problemId) {

        String contestTitle =
                usageRepository
                        .findUsingContestTitle(problemId)
                        .orElse(null);

        if (contestTitle != null) {
            return lockReason(1, 0, 0, contestTitle);
        }

        long submissions =
                usageRepository.countSubmissions(problemId);

        if (submissions > 0) {
            return lockReason(0, submissions, 0, null);
        }

        return lockReason(
                0,
                0,
                usageRepository.countClarifications(problemId),
                null);
    }

    /**
     * The one place the "why can't this be deleted" wording lives, shared by the delete guard and
     * the workspace list so the button text and the endpoint can never disagree.
     */
    private String lockReason(long contests, long submissions, long clarifications,
                              String contestTitle) {

        if (contests > 0) {
            return contestTitle == null
                    ? "problem is used by a contest"
                    : "problem is used by contest \"" + contestTitle + "\"";
        }

        if (submissions > 0) {
            return "problem has " + submissions + " submission(s)";
        }

        if (clarifications > 0) {
            return "problem has " + clarifications + " clarification(s)";
        }

        return null;
    }

    /**
     * Freezes judged semantics while a contest holding this problem is running. Rewriting the
     * limits, the judge mode or the slug mid-contest would re-define the task under everyone
     * who already submitted (and break their links); typo fixes to the prose must still be
     * possible, so title/statement/constraints/editorial stay editable throughout.
     */
    private void requireEditableNow(
            Problem problem,
            ProblemRequest request,
            String newSlug) {

        boolean judgingChanged =
                !Objects.equals(problem.getSlug(), newSlug)
                        || !Objects.equals(
                                problem.getTimeLimitMs(),
                                valueOrDefault(request.timeLimitMs(), 1000))
                        || !Objects.equals(
                                problem.getMemLimitKb(),
                                valueOrDefault(request.memLimitKb(), 262144))
                        || !Objects.equals(
                                problem.getJudgeMode(),
                                normalizeJudgeMode(request.judgeMode()))
                        || !Objects.equals(problem.getFloatEps(), request.floatEps())
                        || !Objects.equals(problem.getCheckerCode(), request.checkerCode());

        if (judgingChanged
                && usageRepository.existsLiveContestForProblem(problem.getId())) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "problem is in a live contest — only title, statement, "
                            + "constraints and editorial may be edited until it ends");
        }
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
                problem.getCreatedBy(),
                problem.isArchived());
    }
}
