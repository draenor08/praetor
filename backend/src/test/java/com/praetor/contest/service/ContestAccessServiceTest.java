package com.praetor.contest.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.praetor.contest.repository.ContestAccessRepository;
import com.praetor.identity.entity.User;
import org.junit.jupiter.api.Test;

/**
 * The contest embargo rule. One problem, four callers: staff, a registered participant, a
 * non-participant, and an anonymous reader. The time logic itself lives in SQL ({@code now()}
 * against the contest window) and is exercised end-to-end; what is pinned here is the decision made
 * around it, because this is the gate three call sites rely on.
 */
class ContestAccessServiceTest {

    private static final long PROBLEM_ID = 100L;

    private final ContestAccessRepository accessRepo = mock(ContestAccessRepository.class);

    private final ContestAccessService service = new ContestAccessService(accessRepo);

    private User user(long id, String role) {
        User u = new User();
        u.setId(id);
        u.setRole(role);
        return u;
    }

    @Test
    void freeProblem_isOpenToEveryone() {
        when(accessRepo.existsUnendedContestForProblem(PROBLEM_ID)).thenReturn(false);

        assertThat(service.mayAccessProblem(PROBLEM_ID, user(7L, "USER"))).isTrue();
        assertThat(service.mayAccessProblem(PROBLEM_ID, null)).isTrue();
    }

    @Test
    void embargoedProblem_isRefusedToANonParticipant() {
        when(accessRepo.existsUnendedContestForProblem(PROBLEM_ID)).thenReturn(true);
        when(accessRepo.existsRunningRegisteredContestForProblem(PROBLEM_ID, 7L)).thenReturn(false);

        assertThat(service.mayAccessProblem(PROBLEM_ID, user(7L, "USER"))).isFalse();
    }

    @Test
    void embargoedProblem_isOpenToARegisteredParticipantWhileItRuns() {
        when(accessRepo.existsUnendedContestForProblem(PROBLEM_ID)).thenReturn(true);
        when(accessRepo.existsRunningRegisteredContestForProblem(PROBLEM_ID, 7L)).thenReturn(true);

        assertThat(service.mayAccessProblem(PROBLEM_ID, user(7L, "USER"))).isTrue();
    }

    @Test
    void embargoedProblem_isRefusedToAnAnonymousReader() {
        when(accessRepo.existsUnendedContestForProblem(PROBLEM_ID)).thenReturn(true);

        assertThat(service.mayAccessProblem(PROBLEM_ID, null)).isFalse();
        // there is no user id to ask about, so the second query is never issued
        verify(accessRepo, never()).existsRunningRegisteredContestForProblem(anyLong(), anyLong());
    }

    @Test
    void staff_bypassTheEmbargoWithoutAskingTheDatabase() {
        assertThat(service.mayAccessProblem(PROBLEM_ID, user(1L, "ADMIN"))).isTrue();
        assertThat(service.mayAccessProblem(PROBLEM_ID, user(2L, "PROBLEM_SETTER"))).isTrue();

        // staff never reach the embargo query at all — they author these contests
        verify(accessRepo, never()).existsUnendedContestForProblem(anyLong());
    }
}
