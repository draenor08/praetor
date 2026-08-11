package com.praetor.problem.service;

import com.praetor.identity.entity.User;
import com.praetor.problem.dto.ProblemRequest;
import com.praetor.problem.entity.Problem;
import com.praetor.problem.repository.ProblemRepository;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProblemServiceTest {

    private final ProblemRepository problemRepository =
            mock(ProblemRepository.class);

    private final ProblemService service =
            new ProblemService(problemRepository);

    @Test
    void problemSetterCanCreateProblem() {

        User setter = user(5L, "PROBLEM_SETTER");

        when(problemRepository.existsBySlug("a-plus-b"))
                .thenReturn(false);

        when(problemRepository.save(any(Problem.class)))
                .thenAnswer(invocation ->
                        invocation.getArgument(0));

        var response =
                service.create(
                        validRequest("a-plus-b"),
                        setter);

        assertThat(response.slug())
                .isEqualTo("a-plus-b");

        assertThat(response.title())
                .isEqualTo("A Plus B");

        assertThat(response.difficulty())
                .isEqualTo(800);

        assertThat(response.createdBy())
                .isEqualTo(5L);

        verify(problemRepository)
                .save(any(Problem.class));
    }

    @Test
    void normalUserCannotCreateProblem() {

        User normalUser =
                user(5L, "USER");

        Throwable t = catchThrowable(() ->
                service.create(
                        validRequest("a-plus-b"),
                        normalUser));

        assertStatus(
                t,
                HttpStatus.FORBIDDEN);

        verify(problemRepository, never())
                .save(any());
    }

    @Test
    void duplicateSlugGets409() {

        User setter =
                user(5L, "PROBLEM_SETTER");

        when(problemRepository.existsBySlug("a-plus-b"))
                .thenReturn(true);

        Throwable t = catchThrowable(() ->
                service.create(
                        validRequest("a-plus-b"),
                        setter));

        assertStatus(
                t,
                HttpStatus.CONFLICT);

        verify(problemRepository, never())
                .save(any());
    }

    @Test
    void problemSetterCanUpdateProblem() {

        User setter =
                user(5L, "PROBLEM_SETTER");

        Problem existing =
                new Problem(
                        "old-problem",
                        "Old Title",
                        "Old statement",
                        null,
                        800,
                        1000,
                        262144,
                        "EXACT",
                        null,
                        null,
                        null,
                        5L);

        when(problemRepository
                .findBySlug("old-problem"))
                .thenReturn(
                        Optional.of(existing));

        when(problemRepository
                .findBySlug("new-problem"))
                .thenReturn(
                        Optional.empty());

        when(problemRepository.save(existing))
                .thenReturn(existing);

        ProblemRequest request =
                new ProblemRequest(
                        "new-problem",
                        "New Title",
                        "New statement",
                        "n <= 100",
                        1200,
                        2000,
                        131072,
                        "TOKEN",
                        null,
                        null,
                        "Editorial");

        var response =
                service.update(
                        "old-problem",
                        request,
                        setter);

        assertThat(response.slug())
                .isEqualTo("new-problem");

        assertThat(response.title())
                .isEqualTo("New Title");

        assertThat(response.difficulty())
                .isEqualTo(1200);

        assertThat(response.judgeMode())
                .isEqualTo("TOKEN");

        verify(problemRepository)
                .save(existing);
    }

    @Test
    void adminCanDeleteProblem() {

        User admin =
                user(1L, "ADMIN");

        Problem problem =
                new Problem(
                        "remove-me",
                        "Remove Me",
                        "statement",
                        null,
                        800,
                        1000,
                        262144,
                        "EXACT",
                        null,
                        null,
                        null,
                        5L);

        when(problemRepository
                .findBySlug("remove-me"))
                .thenReturn(
                        Optional.of(problem));

        service.delete(
                "remove-me",
                admin);

        verify(problemRepository)
                .delete(problem);
    }

    @Test
    void problemSetterCannotDeleteProblem() {

        User setter =
                user(5L, "PROBLEM_SETTER");

        Throwable t = catchThrowable(() ->
                service.delete(
                        "a-plus-b",
                        setter));

        assertStatus(
                t,
                HttpStatus.FORBIDDEN);

        verify(problemRepository, never())
                .delete(any());
    }

    @Test
    void invalidDifficultyGets400() {

        User setter =
                user(5L, "PROBLEM_SETTER");

        ProblemRequest request =
                new ProblemRequest(
                        "bad-problem",
                        "Bad Problem",
                        "statement",
                        null,
                        5000,
                        1000,
                        262144,
                        "EXACT",
                        null,
                        null,
                        null);

        Throwable t = catchThrowable(() ->
                service.create(
                        request,
                        setter));

        assertStatus(
                t,
                HttpStatus.BAD_REQUEST);

        verify(problemRepository, never())
                .save(any());
    }

    @Test
    void floatModeRequiresPositiveEpsilon() {

        User setter =
                user(5L, "PROBLEM_SETTER");

        ProblemRequest request =
                new ProblemRequest(
                        "float-problem",
                        "Float Problem",
                        "statement",
                        null,
                        800,
                        1000,
                        262144,
                        "FLOAT",
                        null,
                        null,
                        null);

        Throwable t = catchThrowable(() ->
                service.create(
                        request,
                        setter));

        assertStatus(
                t,
                HttpStatus.BAD_REQUEST);

        verify(problemRepository, never())
                .save(any());
    }

    private ProblemRequest validRequest(
            String slug) {

        return new ProblemRequest(
                slug,
                "A Plus B",
                "Add two numbers.",
                "1 <= a,b <= 100",
                800,
                1000,
                262144,
                "EXACT",
                null,
                null,
                null);
    }

    private User user(
            Long id,
            String role) {

        User user = new User();
        user.setId(id);
        user.setRole(role);

        return user;
    }

    private void assertStatus(
            Throwable throwable,
            HttpStatus expected) {

        assertThat(throwable)
                .isInstanceOf(
                        ResponseStatusException.class);

        assertThat(
                ((ResponseStatusException) throwable)
                        .getStatusCode())
                .isEqualTo(expected);
    }
}
