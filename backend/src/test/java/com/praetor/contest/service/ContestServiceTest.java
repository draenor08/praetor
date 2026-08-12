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
import com.praetor.contest.repository.ContestProblemRow;
import com.praetor.contest.repository.ContestRepository;
import com.praetor.contest.repository.RegistrationRepository;
import com.praetor.identity.entity.User;
import com.praetor.identity.service.RatingService;

import java.time.ZonedDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class ContestServiceTest {

    private final ContestRepository contestRepo =
            mock(ContestRepository.class);

    private final ContestProblemRepository contestProblemRepo =
            mock(ContestProblemRepository.class);

    private final RegistrationRepository registrationRepo =
            mock(RegistrationRepository.class);

    private final ContestAccessService contestAccess =
            mock(ContestAccessService.class);

    private final ContestService service =
            new ContestService(
                    contestRepo,
                    contestProblemRepo,
                    registrationRepo,
                    contestAccess);

    private final ZonedDateTime start =
            ZonedDateTime.now();

    private final ZonedDateTime end =
            start.plusHours(2);

    private User user(long id, String role) {

        User u = new User();
        u.setId(id);
        u.setRole(role);

        return u;
    }

    private CreateContestRequest req(
            ZonedDateTime s,
            ZonedDateTime e,
            List<ContestProblemSpec> problems) {

        return new CreateContestRequest(
                "Round",
                s,
                e,
                15,
                "ICPC",
                problems);
    }

    private List<ContestProblemSpec> twoProblems() {

        return List.of(
                new ContestProblemSpec(
                        1L,
                        "A",
                        1),
                new ContestProblemSpec(
                        2L,
                        "B",
                        2));
    }

    private void assertStatus(
            Throwable t,
            HttpStatus expected) {

        assertThat(t)
                .isInstanceOf(
                        ResponseStatusException.class);

        assertThat(
                ((ResponseStatusException) t)
                        .getStatusCode())
                .isEqualTo(expected);
    }

    @Test
    void create_adminOk() {

        when(contestRepo.save(any()))
                .thenAnswer(inv ->
                        inv.getArgument(0));

        when(contestProblemRepo
                .findByIdContestIdOrderByOrdAsc(any()))
                .thenReturn(List.of());

        service.create(
                req(
                        start,
                        end,
                        twoProblems()),
                user(1L, "ADMIN"));

        verify(contestRepo)
                .save(any(Contest.class));

        // flushed, not just saved — toResponse reads the slots back with a native query
        verify(contestProblemRepo)
                .saveAllAndFlush(any());
    }

    @Test
    void create_nonAdmin_403() {

        Throwable t =
                org.assertj.core.api.Assertions
                        .catchThrowable(() ->
                                service.create(
                                        req(
                                                start,
                                                end,
                                                twoProblems()),
                                        user(
                                                1L,
                                                "USER")));

        assertStatus(
                t,
                HttpStatus.FORBIDDEN);

        verify(contestRepo, never())
                .save(any());
    }

    @Test
    void create_endsBeforeStarts_400() {

        Throwable t =
                org.assertj.core.api.Assertions
                        .catchThrowable(() ->
                                service.create(
                                        req(
                                                start,
                                                start.minusMinutes(1),
                                                twoProblems()),
                                        user(
                                                1L,
                                                "ADMIN")));

        assertStatus(
                t,
                HttpStatus.BAD_REQUEST);

        verify(contestRepo, never())
                .save(any());
    }

    @Test
    void create_duplicateLabels_400() {

        List<ContestProblemSpec> dup =
                List.of(
                        new ContestProblemSpec(
                                1L,
                                "A",
                                1),
                        new ContestProblemSpec(
                                2L,
                                "A",
                                2));

        Throwable t =
                org.assertj.core.api.Assertions
                        .catchThrowable(() ->
                                service.create(
                                        req(
                                                start,
                                                end,
                                                dup),
                                        user(
                                                1L,
                                                "ADMIN")));

        assertStatus(
                t,
                HttpStatus.BAD_REQUEST);

        verify(contestRepo, never())
                .save(any());
    }

    @Test
    void list_mapsRows() {

        when(contestRepo.findAll())
                .thenReturn(
                        List.of(
                                new Contest(
                                        "Round A",
                                        start,
                                        end,
                                        15,
                                        "ICPC"),
                                new Contest(
                                        "Round B",
                                        start,
                                        end,
                                        0,
                                        "ICPC")));

        var summaries =
                service.list();

        assertThat(summaries)
                .hasSize(2);

        assertThat(
                summaries.get(0).title())
                .isEqualTo("Round A");

        assertThat(
                summaries.get(0).scoring())
                .isEqualTo("ICPC");

        assertThat(
                summaries.get(0).startsAt())
                .isEqualTo(
                        start.toInstant()
                                .toString());
    }

    @Test
    void register_ok() {

        when(contestRepo.existsById(5L))
                .thenReturn(true);

        when(registrationRepo
                .existsById(any()))
                .thenReturn(false);

        service.register(
                5L,
                new RegisterRequest(false),
                user(7L, "USER"));

        verify(registrationRepo)
                .save(any());
    }

    @Test
    void register_duplicate_409() {

        when(contestRepo.existsById(5L))
                .thenReturn(true);

        when(registrationRepo
                .existsById(any()))
                .thenReturn(true);

        Throwable t =
                org.assertj.core.api.Assertions
                        .catchThrowable(() ->
                                service.register(
                                        5L,
                                        new RegisterRequest(false),
                                        user(
                                                7L,
                                                "USER")));

        assertStatus(
                t,
                HttpStatus.CONFLICT);

        verify(registrationRepo, never())
                .save(any());
    }

    @Test
    void register_unknownContest_404() {

        when(contestRepo
                .existsById(anyLong()))
                .thenReturn(false);

        Throwable t =
                org.assertj.core.api.Assertions
                        .catchThrowable(() ->
                                service.register(
                                        99L,
                                        new RegisterRequest(false),
                                        user(
                                                7L,
                                                "USER")));

        assertStatus(
                t,
                HttpStatus.NOT_FOUND);

        verify(registrationRepo, never())
                .save(any());
    }

    @Test
    void register_virtualTrue_400() {

        Throwable t =
                org.assertj.core.api.Assertions
                        .catchThrowable(() ->
                                service.register(
                                        5L,
                                        new RegisterRequest(true),
                                        user(
                                                7L,
                                                "USER")));

        assertStatus(
                t,
                HttpStatus.BAD_REQUEST);

        verify(registrationRepo, never())
                .save(any());
    }

    // ---- contest page: registration flag + the embargo on problem identity ----

    private ContestProblemRow row(String label, int ord, long problemId) {

        ContestProblemRow r =
                mock(ContestProblemRow.class);

        when(r.getLabel()).thenReturn(label);
        when(r.getOrd()).thenReturn(ord);
        when(r.getProblemId()).thenReturn(problemId);
        when(r.getSlug()).thenReturn("a-plus-b");
        when(r.getTitle()).thenReturn("A + B");

        return r;
    }

    private void contestWithOneProblem() {

        Contest c =
                new Contest(
                        "Round",
                        start,
                        end,
                        15,
                        "ICPC");

        // Built on its own line: constructing a stubbed mock inside thenReturn(...) trips Mockito's
        // unfinished-stubbing detector.
        ContestProblemRow slot =
                row("A ", 1, 100L);

        when(contestRepo.findById(5L))
                .thenReturn(java.util.Optional.of(c));

        // any(): the Contest here is never persisted, so getId() is null when toResponse reads back
        when(contestProblemRepo.findRowsByContestId(any()))
                .thenReturn(List.of(slot));
    }

    @Test
    void get_registeredParticipant_seesSlugsAndIsMarkedRegistered() {

        contestWithOneProblem();

        when(registrationRepo.existsById(any()))
                .thenReturn(true);

        when(contestAccess.mayAccessProblem(anyLong(), any()))
                .thenReturn(true);

        var res =
                service.get(
                        5L,
                        user(7L, "USER"));

        assertThat(res.registered()).isTrue();
        assertThat(res.problemsVisible()).isTrue();
        assertThat(res.problems()).hasSize(1);
        assertThat(res.problems().get(0).slug()).isEqualTo("a-plus-b");
        // CHAR(2) pads the label — trimmed here or the link renders "A " with a gap
        assertThat(res.problems().get(0).label()).isEqualTo("A");
    }

    @Test
    void get_embargoed_keepsLabelsButWithholdsSlugAndTitle() {

        contestWithOneProblem();

        when(registrationRepo.existsById(any()))
                .thenReturn(false);

        when(contestAccess.mayAccessProblem(anyLong(), any()))
                .thenReturn(false);

        var res =
                service.get(
                        5L,
                        user(7L, "USER"));

        assertThat(res.registered()).isFalse();
        assertThat(res.problemsVisible()).isFalse();
        // the standings board still needs its columns, so the label survives
        assertThat(res.problems().get(0).label()).isEqualTo("A");
        assertThat(res.problems().get(0).slug()).isNull();
        assertThat(res.problems().get(0).title()).isNull();
    }

    @Test
    void get_anonymousReader_isNeverRegistered() {

        contestWithOneProblem();

        when(contestAccess.mayAccessProblem(anyLong(), any()))
                .thenReturn(false);

        var res =
                service.get(5L, null);

        assertThat(res.registered()).isFalse();
        // no registration lookup is possible without a caller
        verify(registrationRepo, never())
                .existsById(any());
    }

    // Rating a finished contest moved to identity's ContestRatingScheduler — covered by
    // ContestRatingSchedulerTest. The contest module no longer knows rating exists.
}
