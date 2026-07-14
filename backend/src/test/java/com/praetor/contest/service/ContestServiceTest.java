package com.praetor.contest.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.praetor.contest.dto.ContestProblemSpec;
import com.praetor.contest.dto.CreateContestRequest;
import com.praetor.contest.dto.RegisterRequest;
import com.praetor.contest.entity.Contest;
import com.praetor.contest.repository.ContestProblemRepository;
import com.praetor.contest.repository.ContestRepository;
import com.praetor.contest.repository.RegistrationRepository;
import com.praetor.identity.entity.User;
import java.time.ZonedDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class ContestServiceTest {

    private final ContestRepository contestRepo = mock(ContestRepository.class);
    private final ContestProblemRepository contestProblemRepo = mock(ContestProblemRepository.class);
    private final RegistrationRepository registrationRepo = mock(RegistrationRepository.class);

    private final ContestService service =
            new ContestService(contestRepo, contestProblemRepo, registrationRepo);

    private final ZonedDateTime start = ZonedDateTime.now();
    private final ZonedDateTime end = start.plusHours(2);

    private User user(long id, String role) {
        User u = new User();
        u.setId(id);
        u.setRole(role);
        return u;
    }

    private CreateContestRequest req(ZonedDateTime s, ZonedDateTime e, List<ContestProblemSpec> problems) {
        return new CreateContestRequest("Round", s, e, 15, "ICPC", problems);
    }

    private List<ContestProblemSpec> twoProblems() {
        return List.of(new ContestProblemSpec(1L, "A", 1), new ContestProblemSpec(2L, "B", 2));
    }

    private void assertStatus(Throwable t, HttpStatus expected) {
        assertThat(t).isInstanceOf(ResponseStatusException.class);
        assertThat(((ResponseStatusException) t).getStatusCode()).isEqualTo(expected);
    }

    // ---- create ----
    @Test
    void create_adminOk() {
        when(contestRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(contestProblemRepo.findByIdContestIdOrderByOrdAsc(any())).thenReturn(List.of());

        service.create(req(start, end, twoProblems()), user(1L, "ADMIN"));

        verify(contestRepo).save(any(Contest.class));
        verify(contestProblemRepo).saveAll(any());
    }

    @Test
    void create_nonAdmin_403() {
        Throwable t = org.assertj.core.api.Assertions.catchThrowable(
                () -> service.create(req(start, end, twoProblems()), user(1L, "USER")));
        assertStatus(t, HttpStatus.FORBIDDEN);
        verify(contestRepo, never()).save(any());
    }

    @Test
    void create_endsBeforeStarts_400() {
        Throwable t = org.assertj.core.api.Assertions.catchThrowable(
                () -> service.create(req(start, start.minusMinutes(1), twoProblems()), user(1L, "ADMIN")));
        assertStatus(t, HttpStatus.BAD_REQUEST);
        verify(contestRepo, never()).save(any());
    }

    @Test
    void create_duplicateLabels_400() {
        List<ContestProblemSpec> dup = List.of(
                new ContestProblemSpec(1L, "A", 1), new ContestProblemSpec(2L, "A", 2));
        Throwable t = org.assertj.core.api.Assertions.catchThrowable(
                () -> service.create(req(start, end, dup), user(1L, "ADMIN")));
        assertStatus(t, HttpStatus.BAD_REQUEST);
        verify(contestRepo, never()).save(any());
    }

    // ---- register ----
    @Test
    void register_ok() {
        when(contestRepo.existsById(5L)).thenReturn(true);
        when(registrationRepo.existsById(any())).thenReturn(false);

        service.register(5L, new RegisterRequest(false), user(7L, "USER"));

        verify(registrationRepo).save(any());
    }

    @Test
    void register_duplicate_409() {
        when(contestRepo.existsById(5L)).thenReturn(true);
        when(registrationRepo.existsById(any())).thenReturn(true);

        Throwable t = org.assertj.core.api.Assertions.catchThrowable(
                () -> service.register(5L, new RegisterRequest(false), user(7L, "USER")));
        assertStatus(t, HttpStatus.CONFLICT);
        verify(registrationRepo, never()).save(any());
    }

    @Test
    void register_unknownContest_404() {
        when(contestRepo.existsById(anyLong())).thenReturn(false);

        Throwable t = org.assertj.core.api.Assertions.catchThrowable(
                () -> service.register(99L, new RegisterRequest(false), user(7L, "USER")));
        assertStatus(t, HttpStatus.NOT_FOUND);
        verify(registrationRepo, never()).save(any());
    }

    @Test
    void register_virtualTrue_400() {
        Throwable t = org.assertj.core.api.Assertions.catchThrowable(
                () -> service.register(5L, new RegisterRequest(true), user(7L, "USER")));
        assertStatus(t, HttpStatus.BAD_REQUEST);
        verify(registrationRepo, never()).save(any());
    }
}
