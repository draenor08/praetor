package com.praetor.identity.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.praetor.identity.dto.ProfileSolveStats;
import com.praetor.identity.entity.User;
import com.praetor.identity.repository.UserRepository;
import com.praetor.submission.repository.SubmissionRepository;
import com.praetor.submission.repository.VerdictCountView;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * Solve statistics (FR-25). The contest-end filter that keeps these counts from leaking a frozen
 * board lives in SQL, so it is verified end to end by {@code scripts/e2e.mjs} rather than here —
 * what these tests pin down is the arithmetic and the shape, which is where the shipped version
 * was wrong: it divided distinct problems solved by total submissions, mixing two different units.
 */
class ProfileServiceTest {

    private static final long USER_ID = 7L;

    private final UserRepository userRepo = mock(UserRepository.class);
    private final SubmissionRepository subRepo = mock(SubmissionRepository.class);

    private final ProfileService service = new ProfileService(userRepo, subRepo);

    /**
     * A tally row is a value, not a collaborator. Mocking it would mean calling when() inside the
     * argument list of another thenReturn(), which Mockito reads as nested stubbing and rejects.
     */
    private record Tally(String verdict, long total) implements VerdictCountView {

        @Override
        public String getVerdict() {
            return verdict;
        }

        @Override
        public long getTotal() {
            return total;
        }
    }

    private static VerdictCountView row(String verdict, long total) {
        return new Tally(verdict, total);
    }

    private void userExists() {
        User u = new User();
        u.setId(USER_ID);
        u.setUsername("alice");
        when(userRepo.findByUsername("alice")).thenReturn(Optional.of(u));
    }

    @Test
    void accuracyIsAcceptedSubmissionsOverAttempted_notProblemsOverSubmissions() {
        userExists();
        // 5 accepted attempts at a single problem, 10 judged submissions overall.
        when(subRepo.tallyVerdictsForUser(USER_ID))
                .thenReturn(List.of(row("AC", 5L), row("WA", 5L)));
        when(subRepo.countDistinctSolvedProblems(USER_ID)).thenReturn(1L);

        ProfileSolveStats stats = service.getSolveStats("alice");

        assertThat(stats.attempted()).isEqualTo(10L);
        assertThat(stats.accuracy()).isEqualTo(0.5);   // not 1/10 = 0.1
        assertThat(stats.solved()).isEqualTo(1L);
    }

    @Test
    void byVerdictSumsToAttemptedAndIsOrderedCommonestFirst() {
        userExists();
        when(subRepo.tallyVerdictsForUser(USER_ID))
                .thenReturn(List.of(row("TLE", 7L), row("AC", 42L), row("WA", 18L)));
        when(subRepo.countDistinctSolvedProblems(USER_ID)).thenReturn(30L);

        ProfileSolveStats stats = service.getSolveStats("alice");

        assertThat(stats.byVerdict()).containsExactly(
                java.util.Map.entry("AC", 42L),
                java.util.Map.entry("WA", 18L),
                java.util.Map.entry("TLE", 7L));
        assertThat(stats.attempted())
                .isEqualTo(stats.byVerdict().values().stream().mapToLong(Long::longValue).sum());
    }

    @Test
    void nothingJudgedYet_isZeroAccuracyRatherThanDivideByZero() {
        userExists();
        when(subRepo.tallyVerdictsForUser(USER_ID)).thenReturn(List.of());
        when(subRepo.countDistinctSolvedProblems(USER_ID)).thenReturn(0L);

        ProfileSolveStats stats = service.getSolveStats("alice");

        assertThat(stats.attempted()).isZero();
        assertThat(stats.accuracy()).isZero();
        assertThat(stats.solved()).isZero();
        assertThat(stats.byVerdict()).isEmpty();
    }

    @Test
    void solvedComesFromTheDistinctProblemCount_notFromTheAcceptedTally() {
        userExists();
        // 9 accepted submissions spread over 3 problems.
        when(subRepo.tallyVerdictsForUser(USER_ID)).thenReturn(List.of(row("AC", 9L)));
        when(subRepo.countDistinctSolvedProblems(USER_ID)).thenReturn(3L);

        ProfileSolveStats stats = service.getSolveStats("alice");

        assertThat(stats.solved()).isEqualTo(3L);
        assertThat(stats.attempted()).isEqualTo(9L);
        assertThat(stats.accuracy()).isEqualTo(1.0);
    }

    @Test
    void unknownHandle_is404_notA500() {
        when(userRepo.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getSolveStats("ghost"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);

        // No point counting submissions for a user that does not exist.
        verifyNoInteractions(subRepo);
    }

    @Test
    void accuracyIsRoundedRatherThanCarryingFloatArtefacts() {
        userExists();
        when(subRepo.tallyVerdictsForUser(USER_ID))
                .thenReturn(List.of(row("AC", 1L), row("WA", 2L)));
        when(subRepo.countDistinctSolvedProblems(anyLong())).thenReturn(1L);

        ProfileSolveStats stats = service.getSolveStats("alice");

        assertThat(stats.accuracy()).isEqualTo(0.3333);
    }
}
