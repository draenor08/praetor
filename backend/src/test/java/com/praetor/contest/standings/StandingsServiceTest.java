package com.praetor.contest.standings;

import com.praetor.contest.dto.StandingsResponse;
import com.praetor.contest.entity.Contest;
import com.praetor.contest.entity.ContestProblem;
import com.praetor.contest.entity.Registration;
import com.praetor.contest.entity.RegistrationId;
import com.praetor.contest.repository.ContestProblemRepository;
import com.praetor.contest.repository.ContestRepository;
import com.praetor.contest.repository.RegistrationRepository;
import com.praetor.contest.repository.StandingsRepository;
import com.praetor.identity.entity.User;
import com.praetor.identity.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The assembly step between the repositories and {@link StandingsCalculator}. The fold itself is
 * covered exhaustively by {@link StandingsCalculatorTest}; what is asserted here is what this layer
 * alone decides — who appears on the board at all, in what order, and that the privileged flag is
 * handed down rather than reinterpreted.
 */
class StandingsServiceTest {

    private final ContestRepository contestRepo = mock(ContestRepository.class);
    private final ContestProblemRepository contestProblemRepo = mock(ContestProblemRepository.class);
    private final RegistrationRepository registrationRepo = mock(RegistrationRepository.class);
    private final StandingsRepository standingsRepo = mock(StandingsRepository.class);
    private final UserRepository userRepo = mock(UserRepository.class);

    private final StandingsService service = new StandingsService(
            contestRepo, contestProblemRepo, registrationRepo, standingsRepo, userRepo,
            new StandingsCalculator());

    @Test
    void anUnknownContestIs404() {

        when(contestRepo.findById(1L)).thenReturn(Optional.empty());

        Throwable t = catchThrowable(() -> service.snapshot(1L, false));

        assertThat(t).isInstanceOf(ResponseStatusException.class);
        assertThat(((ResponseStatusException) t).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void onlyRegisteredUsersAppearOnTheBoard() {

        givenContest(0);
        givenProblems("A");
        givenRegistrations(2L, 3L);
        // The repository is asked only for the registered ids; a non-participant cannot be returned.
        givenUsers(user(2L, "bob"), user(3L, "alice"));
        when(standingsRepo.findJudged(1L)).thenReturn(List.of());

        StandingsResponse board = service.snapshot(1L, false);

        assertThat(board.rows()).extracting("handle").containsExactlyInAnyOrder("alice", "bob");
        verify(userRepo).findAllById(List.of(2L, 3L));
    }

    @Test
    void aContestWithNoRegistrationsProducesAnEmptyBoardRatherThanAnError() {

        givenContest(0);
        givenProblems("A");
        when(registrationRepo.findByIdContestId(1L)).thenReturn(List.of());
        when(userRepo.findAllById(List.of())).thenReturn(List.of());
        when(standingsRepo.findJudged(1L)).thenReturn(List.of());

        StandingsResponse board = service.snapshot(1L, false);

        assertThat(board.rows()).isEmpty();
        assertThat(board.contestId()).isEqualTo(1L);
    }

    @Test
    void problemColumnsKeepTheirContestOrder() {

        givenContest(0);
        givenProblems("A", "B", "C");
        givenRegistrations(2L);
        givenUsers(user(2L, "alice"));
        when(standingsRepo.findJudged(1L)).thenReturn(List.of());

        StandingsResponse board = service.snapshot(1L, false);

        assertThat(board.rows().get(0).problems()).extracting("label")
                .containsExactly("A", "B", "C");
    }

    @Test
    void theFreezeFlagReportsTheWindowRegardlessOfWhoIsLooking() {

        // A freeze window covering now: started an hour ago, ends in an hour, freeze the last 90 min.
        givenContest(90);
        givenProblems("A");
        givenRegistrations(2L);
        givenUsers(user(2L, "alice"));
        when(standingsRepo.findJudged(1L)).thenReturn(List.of());

        // Both viewers are told the window is active; what differs is what they are shown, which is
        // the calculator's job and is asserted in its own suite.
        assertThat(service.snapshot(1L, false).frozen()).isTrue();
        assertThat(service.snapshot(1L, true).frozen()).isTrue();
    }

    // --- fixtures ---------------------------------------------------------------------------

    private void givenContest(int freezeMin) {
        Contest c = mock(Contest.class);
        when(c.getStartsAt()).thenReturn(ZonedDateTime.now().minusHours(1));
        when(c.getEndsAt()).thenReturn(ZonedDateTime.now().plusHours(1));
        when(c.getFreezeMin()).thenReturn(freezeMin);
        when(contestRepo.findById(1L)).thenReturn(Optional.of(c));
    }

    private void givenProblems(String... labels) {
        List<ContestProblem> problems = new java.util.ArrayList<>();
        for (int i = 0; i < labels.length; i++) {
            ContestProblem cp = mock(ContestProblem.class);
            when(cp.getLabel()).thenReturn(labels[i]);
            when(cp.getOrd()).thenReturn(i + 1);
            when(cp.getProblemId()).thenReturn((long) (100 + i));
            problems.add(cp);
        }
        when(contestProblemRepo.findByIdContestIdOrderByOrdAsc(1L)).thenReturn(problems);
    }

    private void givenRegistrations(Long... userIds) {
        List<Registration> regs = new java.util.ArrayList<>();
        for (Long id : userIds) {
            Registration r = mock(Registration.class);
            when(r.getId()).thenReturn(new RegistrationId(1L, id));
            regs.add(r);
        }
        when(registrationRepo.findByIdContestId(1L)).thenReturn(regs);
    }

    private void givenUsers(User... users) {
        when(userRepo.findAllById(org.mockito.ArgumentMatchers.anyIterable()))
                .thenReturn(List.of(users));
    }

    private User user(Long id, String username) {
        User u = new User();
        u.setId(id);
        u.setUsername(username);
        return u;
    }
}
