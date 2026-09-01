package com.praetor.problem.service;

import com.praetor.contest.service.ContestAccessService;
import com.praetor.identity.entity.User;
import com.praetor.problem.dto.ProblemDetail;
import com.praetor.problem.dto.ProblemPage;
import com.praetor.problem.dto.ProblemSummary;
import com.praetor.problem.dto.SampleDto;
import com.praetor.problem.entity.ProblemView;
import com.praetor.problem.repository.ProblemTagRepository;
import com.praetor.problem.repository.ProblemViewRepository;
import com.praetor.submission.entity.JudgeTestCase;
import com.praetor.submission.repository.JudgeTestCaseRepository;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Read-only problem access for the frontend (list + statement page). Part of the problem-read
 * W-shim; see {@link ProblemView}. Samples are drawn from the engine's {@code test_cases} view,
 * filtered to the {@code SAMPLE} kind so hidden test inputs are never exposed.
 */
@Service
public class ProblemReadService {

    /** Visible test-case kind (the only one shown to users). */
    private static final String SAMPLE = "SAMPLE";

    /** Enough to describe a problem; more is a sign the tag list is being used as a notes field. */
    private static final int MAX_TAG_FILTERS = 8;

    /** Same ceiling the submission history uses, for the same reason: one request, bounded work. */
    private static final int MAX_PAGE_SIZE = 100;

    private final ProblemViewRepository problemRepo;
    private final ProblemTagRepository tagRepo;
    private final JudgeTestCaseRepository testCaseRepo;
    private final ContestAccessService contestAccess;

    public ProblemReadService(ProblemViewRepository problemRepo, ProblemTagRepository tagRepo,
                              JudgeTestCaseRepository testCaseRepo,
                              ContestAccessService contestAccess) {
        this.problemRepo = problemRepo;
        this.tagRepo = tagRepo;
        this.testCaseRepo = testCaseRepo;
        this.contestAccess = contestAccess;
    }

    /**
     * The problem list, optionally filtered by text, difficulty range and tags (FR-15). Staff see
     * every unarchived problem; everyone else sees the list minus problems under contest embargo,
     * which reappear once the contest using them ends. Filtering happens in the same SQL as the
     * embargo, so no filter combination can surface a withheld problem.
     *
     * @param user the caller, or {@code null} for an anonymous reader
     */
    @Transactional(readOnly = true)
    public ProblemPage list(User user, String q, Integer minDifficulty,
                            Integer maxDifficulty, List<String> tags, int page, int size) {

        List<String> wanted = normalizeTagFilter(tags);
        if (minDifficulty != null && maxDifficulty != null && minDifficulty > maxDifficulty) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "minDifficulty must not exceed maxDifficulty");
        }
        if (page < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "page must be >= 0");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "size must be between 1 and " + MAX_PAGE_SIZE);
        }

        boolean staff = contestAccess.isStaff(user);
        String text = q == null ? "" : q.trim();
        String tagCsv = String.join(",", wanted);

        List<ProblemSummary> content = problemRepo.search(
                        staff, text, minDifficulty, maxDifficulty, tagCsv, wanted.size(),
                        size, page * size)
                .stream()
                .map(r -> new ProblemSummary(r.getSlug(), r.getTitle(), r.getDifficulty(),
                        r.getJudgeMode(), splitTags(r.getTags())))
                .toList();

        long total = problemRepo.countMatching(
                staff, text, minDifficulty, maxDifficulty, tagCsv, wanted.size());

        return new ProblemPage(content, page, size, total);
    }

    /** Every tag in use, for the filter control. */
    @Transactional(readOnly = true)
    public List<String> allTags() {
        return tagRepo.findAllTagNames();
    }

    /**
     * One problem's statement. An embargoed problem is refused with 403 unless the caller is staff
     * or is registered for the contest that is running it — otherwise a participant-only statement
     * would be one typed slug away.
     *
     * @param user the caller, or {@code null} for an anonymous reader
     */
    @Transactional(readOnly = true)
    public ProblemDetail get(String slug, User user) {
        ProblemView p = problemRepo.findBySlug(slug)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "problem not found: " + slug));

        if (!contestAccess.mayAccessProblem(p.getId(), user)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "this problem is part of a contest that has not ended");
        }

        List<SampleDto> samples = testCaseRepo.findByProblemIdOrderByOrdAsc(p.getId()).stream()
                .filter(tc -> SAMPLE.equals(tc.getKind()))
                .map(this::toSample)
                .toList();

        return new ProblemDetail(p.getSlug(), p.getTitle(), p.getStatement(), p.getConstraints(),
                p.getDifficulty(), p.getTimeLimitMs(), p.getMemLimitKb(), p.getJudgeMode(),
                tagRepo.findTagNamesByProblemId(p.getId()), editorialFor(p, user), samples);
    }

    /**
     * The FR-16 gate. Staff wrote the editorials, so they always read them. For everyone else it is
     * withheld while any contest is using the problem — reaching this method during a round means
     * the caller is a registered participant, and handing a participant the solution mid-contest is
     * worse than handing them the statement — and otherwise only once they have solved it
     * themselves. Keyed on the caller's own accepted submission, so it cannot be borrowed.
     */
    private String editorialFor(ProblemView p, User user) {
        if (p.getEditorial() == null || p.getEditorial().isBlank()) {
            return null;
        }
        if (contestAccess.isStaff(user)) {
            return p.getEditorial();
        }
        if (user == null || contestAccess.isContested(p.getId())) {
            return null;
        }
        return problemRepo.existsAcceptedSubmission(p.getId(), user.getId())
                ? p.getEditorial()
                : null;
    }

    /** Lowercased, trimmed, de-duplicated, blank-free — matching how tags are stored. */
    private List<String> normalizeTagFilter(List<String> tags) {
        if (tags == null) {
            return List.of();
        }
        List<String> cleaned = tags.stream()
                .filter(t -> t != null && !t.isBlank())
                .map(t -> t.trim().toLowerCase())
                .distinct()
                .toList();
        if (cleaned.size() > MAX_TAG_FILTERS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "at most " + MAX_TAG_FILTERS + " tags may be filtered on");
        }
        // A tag name containing a comma would split into two filters inside the SQL, silently
        // widening the match. Tag creation rejects commas, so this can only arrive hand-crafted.
        if (cleaned.stream().anyMatch(t -> t.contains(","))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "tag names cannot contain commas");
        }
        return cleaned;
    }

    private List<String> splitTags(String csv) {
        return (csv == null || csv.isBlank()) ? List.of() : List.of(csv.split(","));
    }

    private SampleDto toSample(JudgeTestCase tc) {
        return new SampleDto(tc.getOrd(), tc.getInput(), tc.getExpected());
    }
}
