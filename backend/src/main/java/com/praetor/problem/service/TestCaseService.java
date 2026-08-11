package com.praetor.problem.service;

import com.praetor.identity.entity.User;
import com.praetor.problem.dto.BulkTestCaseRequest;
import com.praetor.problem.dto.TestCaseItem;
import com.praetor.problem.dto.TestCaseResponse;
import com.praetor.problem.entity.ProblemView;
import com.praetor.problem.entity.TestCase;
import com.praetor.problem.repository.ProblemUsageRepository;
import com.praetor.problem.repository.ProblemViewRepository;
import com.praetor.problem.repository.TestCaseRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class TestCaseService {

    private final ProblemViewRepository problemRepository;
    private final TestCaseRepository testCaseRepository;
    private final ProblemUsageRepository usageRepository;

    public TestCaseService(
            ProblemViewRepository problemRepository,
            TestCaseRepository testCaseRepository,
            ProblemUsageRepository usageRepository) {

        this.problemRepository = problemRepository;
        this.testCaseRepository = testCaseRepository;
        this.usageRepository = usageRepository;
    }

    @Transactional(readOnly = true)
    public List<TestCaseResponse> getTestCases(
            String slug,
            User user) {

        ProblemAuthz.requireStaff(user, "manage test cases");

        ProblemView problem = findProblem(slug);

        return testCaseRepository
                .findByProblemIdOrderByOrdAsc(problem.getId())
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public List<TestCaseResponse> bulkUpdate(
            String slug,
            BulkTestCaseRequest request,
            User user) {

        ProblemAuthz.requireStaff(user, "manage test cases");

        ProblemView problem = findProblem(slug);

        // A REPLACE mid-contest silently invalidates every verdict already awarded, and an
        // APPEND adds a case the earlier submissions were never judged against. Either way the
        // contest stops being the same contest, so writes are frozen while one is running.
        if (usageRepository.existsLiveContestForProblem(problem.getId())) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "problem is in a live contest — test cases are frozen until it ends");
        }

        validateRequest(request);

        String mode = request.mode()
                .trim()
                .toUpperCase();

        if ("REPLACE".equals(mode)) {

            testCaseRepository.deleteByProblemId(
                    problem.getId());

        } else if ("APPEND".equals(mode)) {

            validateAppendOrders(
                    problem.getId(),
                    request.cases());

        } else {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "mode must be APPEND or REPLACE");
        }

        List<TestCase> cases =
                request.cases()
                        .stream()
                        .map(item ->
                                new TestCase(
                                        problem.getId(),
                                        item.ord(),
                                        item.kind()
                                                .trim()
                                                .toUpperCase(),
                                        item.input(),
                                        item.expected(),
                                        item.points() == null
                                                ? 0
                                                : item.points()))
                        .toList();

        testCaseRepository.saveAll(cases);

        return testCaseRepository
                .findByProblemIdOrderByOrdAsc(
                        problem.getId())
                .stream()
                .map(this::toResponse)
                .toList();
    }

    private void validateRequest(
            BulkTestCaseRequest request) {

        if (request == null
                || request.mode() == null
                || request.mode().isBlank()) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "mode is required");
        }

        if (request.cases() == null
                || request.cases().isEmpty()) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "cases must not be empty");
        }

        Set<Integer> orders =
                new HashSet<>();

        for (TestCaseItem item : request.cases()) {

            if (item == null) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "test case must not be null");
            }

            if (item.ord() == null
                    || item.ord() < 1) {

                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "ord must be >= 1");
            }

            if (!orders.add(item.ord())) {

                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "duplicate ord in request");
            }

            if (item.kind() == null
                    || (!"SAMPLE".equalsIgnoreCase(
                            item.kind().trim())
                    && !"HIDDEN".equalsIgnoreCase(
                            item.kind().trim()))) {

                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "kind must be SAMPLE or HIDDEN");
            }

            if (item.input() == null) {

                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "input is required");
            }

            if (item.expected() == null) {

                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "expected is required");
            }

            if (item.points() != null
                    && item.points() < 0) {

                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "points must be >= 0");
            }
        }
    }

    private void validateAppendOrders(
            Long problemId,
            List<TestCaseItem> newCases) {

        Set<Integer> existingOrders =
                testCaseRepository
                        .findByProblemIdOrderByOrdAsc(problemId)
                        .stream()
                        .map(TestCase::getOrd)
                        .collect(
                                java.util.stream.Collectors.toSet());

        boolean conflict =
                newCases.stream()
                        .anyMatch(item ->
                                existingOrders.contains(
                                        item.ord()));

        if (conflict) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "test case ord already exists");
        }
    }

    private ProblemView findProblem(
            String slug) {

        return problemRepository
                .findBySlug(slug)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "problem not found"));
    }

    private TestCaseResponse toResponse(
            TestCase testCase) {

        return new TestCaseResponse(
                testCase.getId(),
                testCase.getOrd(),
                testCase.getKind(),
                testCase.getInput(),
                testCase.getExpected(),
                testCase.getPoints());
    }
}
