package com.praetor.problem.service;

import com.praetor.problem.dto.ProblemDetail;
import com.praetor.problem.dto.ProblemSummary;
import com.praetor.problem.dto.SampleDto;
import com.praetor.problem.entity.ProblemView;
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

    private final ProblemViewRepository problemRepo;
    private final JudgeTestCaseRepository testCaseRepo;

    public ProblemReadService(ProblemViewRepository problemRepo, JudgeTestCaseRepository testCaseRepo) {
        this.problemRepo = problemRepo;
        this.testCaseRepo = testCaseRepo;
    }

    @Transactional(readOnly = true)
    public List<ProblemSummary> list() {
        return problemRepo.findAllByOrderByDifficultyAscTitleAsc().stream()
                .map(p -> new ProblemSummary(p.getSlug(), p.getTitle(), p.getDifficulty(), p.getJudgeMode()))
                .toList();
    }

    @Transactional(readOnly = true)
    public ProblemDetail get(String slug) {
        ProblemView p = problemRepo.findBySlug(slug)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "problem not found: " + slug));

        List<SampleDto> samples = testCaseRepo.findByProblemIdOrderByOrdAsc(p.getId()).stream()
                .filter(tc -> SAMPLE.equals(tc.getKind()))
                .map(this::toSample)
                .toList();

        return new ProblemDetail(p.getSlug(), p.getTitle(), p.getStatement(), p.getConstraints(),
                p.getDifficulty(), p.getTimeLimitMs(), p.getMemLimitKb(), p.getJudgeMode(), samples);
    }

    private SampleDto toSample(JudgeTestCase tc) {
        return new SampleDto(tc.getOrd(), tc.getInput(), tc.getExpected());
    }
}
