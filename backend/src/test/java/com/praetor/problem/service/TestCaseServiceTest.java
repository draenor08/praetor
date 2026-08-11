package com.praetor.problem.service;

import com.praetor.identity.entity.User;
import com.praetor.problem.dto.BulkTestCaseRequest;
import com.praetor.problem.dto.TestCaseItem;
import com.praetor.problem.entity.ProblemView;
import com.praetor.problem.entity.TestCase;
import com.praetor.problem.repository.ProblemViewRepository;
import com.praetor.problem.repository.TestCaseRepository;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TestCaseServiceTest {

    private final ProblemViewRepository problemRepository =
            mock(ProblemViewRepository.class);

    private final TestCaseRepository testCaseRepository =
            mock(TestCaseRepository.class);

    private final TestCaseService service =
            new TestCaseService(
                    problemRepository,
                    testCaseRepository);

    @Test
    void getTestCasesProblemSetterAllowed() {

        User setter = user("PROBLEM_SETTER");
        ProblemView problem = problem(10L);

        TestCase testCase =
                new TestCase(
                        10L,
                        1,
                        "SAMPLE",
                        "1 2",
                        "3",
                        0);

        when(problemRepository.findBySlug("a-plus-b"))
                .thenReturn(Optional.of(problem));

        when(testCaseRepository
                .findByProblemIdOrderByOrdAsc(10L))
                .thenReturn(List.of(testCase));

        var result =
                service.getTestCases(
                        "a-plus-b",
                        setter);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).ord()).isEqualTo(1);
        assertThat(result.get(0).kind())
                .isEqualTo("SAMPLE");
    }

    @Test
    void normalUserGets403() {

        User normalUser = user("USER");

        Throwable t = catchThrowable(() ->
                service.getTestCases(
                        "a-plus-b",
                        normalUser));

        assertStatus(
                t,
                HttpStatus.FORBIDDEN);

        verify(problemRepository, never())
                .findBySlug(any());
    }

    @Test
    void appendAddsCasesWithoutDeletingExisting() {

        User setter = user("PROBLEM_SETTER");
        ProblemView problem = problem(10L);

        when(problemRepository.findBySlug("a-plus-b"))
                .thenReturn(Optional.of(problem));

        when(testCaseRepository
                .findByProblemIdOrderByOrdAsc(10L))
                .thenReturn(
                        List.of(),
                        List.of(
                                new TestCase(
                                        10L,
                                        1,
                                        "SAMPLE",
                                        "1 2",
                                        "3",
                                        0)));

        BulkTestCaseRequest request =
                new BulkTestCaseRequest(
                        "APPEND",
                        List.of(
                                new TestCaseItem(
                                        1,
                                        "SAMPLE",
                                        "1 2",
                                        "3",
                                        0)));

        var result =
                service.bulkUpdate(
                        "a-plus-b",
                        request,
                        setter);

        verify(testCaseRepository, never())
                .deleteByProblemId(10L);

        verify(testCaseRepository)
                .saveAll(any());

        assertThat(result).hasSize(1);
    }

    @Test
    void replaceDeletesOldCasesBeforeSaving() {

        User setter = user("PROBLEM_SETTER");
        ProblemView problem = problem(10L);

        when(problemRepository.findBySlug("a-plus-b"))
                .thenReturn(Optional.of(problem));

        when(testCaseRepository
                .findByProblemIdOrderByOrdAsc(10L))
                .thenReturn(
                        List.of(
                                new TestCase(
                                        10L,
                                        1,
                                        "HIDDEN",
                                        "5",
                                        "10",
                                        0)));

        BulkTestCaseRequest request =
                new BulkTestCaseRequest(
                        "REPLACE",
                        List.of(
                                new TestCaseItem(
                                        1,
                                        "HIDDEN",
                                        "5",
                                        "10",
                                        0)));

        service.bulkUpdate(
                "a-plus-b",
                request,
                setter);

        verify(testCaseRepository)
                .deleteByProblemId(10L);

        verify(testCaseRepository)
                .saveAll(any());
    }

    @Test
    void appendDuplicateExistingOrderGets409() {

        User setter = user("PROBLEM_SETTER");
        ProblemView problem = problem(10L);

        when(problemRepository.findBySlug("a-plus-b"))
                .thenReturn(Optional.of(problem));

        when(testCaseRepository
                .findByProblemIdOrderByOrdAsc(10L))
                .thenReturn(
                        List.of(
                                new TestCase(
                                        10L,
                                        1,
                                        "HIDDEN",
                                        "x",
                                        "y",
                                        0)));

        BulkTestCaseRequest request =
                new BulkTestCaseRequest(
                        "APPEND",
                        List.of(
                                new TestCaseItem(
                                        1,
                                        "SAMPLE",
                                        "1",
                                        "1",
                                        0)));

        Throwable t = catchThrowable(() ->
                service.bulkUpdate(
                        "a-plus-b",
                        request,
                        setter));

        assertStatus(
                t,
                HttpStatus.CONFLICT);

        verify(testCaseRepository, never())
                .saveAll(any());
    }

    @Test
    void invalidKindGets400() {

        User setter = user("PROBLEM_SETTER");
        ProblemView problem = problem(10L);

        when(problemRepository.findBySlug("a-plus-b"))
                .thenReturn(Optional.of(problem));

        BulkTestCaseRequest request =
                new BulkTestCaseRequest(
                        "APPEND",
                        List.of(
                                new TestCaseItem(
                                        1,
                                        "PUBLIC",
                                        "1",
                                        "1",
                                        0)));

        Throwable t = catchThrowable(() ->
                service.bulkUpdate(
                        "a-plus-b",
                        request,
                        setter));

        assertStatus(
                t,
                HttpStatus.BAD_REQUEST);

        verify(testCaseRepository, never())
                .saveAll(any());
    }

    @Test
    void unknownProblemGets404() {

        User setter = user("PROBLEM_SETTER");

        when(problemRepository.findBySlug("missing"))
                .thenReturn(Optional.empty());

        BulkTestCaseRequest request =
                new BulkTestCaseRequest(
                        "APPEND",
                        List.of(
                                new TestCaseItem(
                                        1,
                                        "SAMPLE",
                                        "1",
                                        "1",
                                        0)));

        Throwable t = catchThrowable(() ->
                service.bulkUpdate(
                        "missing",
                        request,
                        setter));

        assertStatus(
                t,
                HttpStatus.NOT_FOUND);

        verify(testCaseRepository, never())
                .saveAll(any());
    }

    private User user(String role) {

        User user = new User();
        user.setRole(role);

        return user;
    }

    private ProblemView problem(Long id) {

        ProblemView problem =
                mock(ProblemView.class);

        when(problem.getId())
                .thenReturn(id);

        return problem;
    }

    private void assertStatus(
            Throwable throwable,
            HttpStatus status) {

        assertThat(throwable)
                .isInstanceOf(
                        ResponseStatusException.class);

        assertThat(
                ((ResponseStatusException) throwable)
                        .getStatusCode())
                .isEqualTo(status);
    }
}
