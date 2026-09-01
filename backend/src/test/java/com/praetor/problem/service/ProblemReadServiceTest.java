package com.praetor.problem.service;

import com.praetor.contest.service.ContestAccessService;
import com.praetor.identity.entity.User;
import com.praetor.problem.dto.ProblemDetail;
import com.praetor.problem.entity.ProblemView;
import com.praetor.problem.repository.ProblemTagRepository;
import com.praetor.problem.repository.ProblemViewRepository;
import com.praetor.submission.entity.JudgeTestCase;
import com.praetor.submission.repository.JudgeTestCaseRepository;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The FR-16 editorial gate, branch by branch, plus the read path's refusals.
 *
 * <p>The gate decides who may read a solution, so each of its exits is asserted on its own rather
 * than through one happy-path case: staff always, nobody while a contest is using the problem,
 * anonymous never, and everyone else only once they have solved it themselves.
 */
class ProblemReadServiceTest {

    private static final String EDITORIAL = "Sort, then two pointers.";

    private final ProblemViewRepository problemRepo = mock(ProblemViewRepository.class);
    private final ProblemTagRepository tagRepo = mock(ProblemTagRepository.class);
    private final JudgeTestCaseRepository testCaseRepo = mock(JudgeTestCaseRepository.class);
    private final ContestAccessService contestAccess = mock(ContestAccessService.class);

    private final ProblemReadService service =
            new ProblemReadService(problemRepo, tagRepo, testCaseRepo, contestAccess);

    // --- FR-16: the editorial gate ---------------------------------------------------------

    @Test
    void staffReadTheEditorialEvenWhileAContestIsUsingTheProblem() {

        User setter = user(1L, "PROBLEM_SETTER");
        givenProblem(EDITORIAL);
        when(contestAccess.mayAccessProblem(7L, setter)).thenReturn(true);
        when(contestAccess.isStaff(setter)).thenReturn(true);

        ProblemDetail detail = service.get("two-sum", setter);

        assertThat(detail.editorial()).isEqualTo(EDITORIAL);
        // Staff short-circuit before the contest and solved checks are even consulted.
        verify(contestAccess, never()).isContested(7L);
        verify(problemRepo, never()).existsAcceptedSubmission(7L, 1L);
    }

    @Test
    void anAnonymousReaderNeverGetsTheEditorial() {

        givenProblem(EDITORIAL);
        when(contestAccess.mayAccessProblem(7L, null)).thenReturn(true);
        when(contestAccess.isStaff(null)).thenReturn(false);

        assertThat(service.get("two-sum", null).editorial()).isNull();
    }

    @Test
    void aParticipantWhoSolvedItIsStillRefusedWhileTheProblemIsContested() {

        User alice = user(2L, "USER");
        givenProblem(EDITORIAL);
        when(contestAccess.mayAccessProblem(7L, alice)).thenReturn(true);
        when(contestAccess.isStaff(alice)).thenReturn(false);
        when(contestAccess.isContested(7L)).thenReturn(true);

        assertThat(service.get("two-sum", alice).editorial()).isNull();
        // Having solved it must not buy the solution mid-round, so the check is never reached.
        verify(problemRepo, never()).existsAcceptedSubmission(7L, 2L);
    }

    @Test
    void aUserWhoHasNotSolvedItDoesNotGetTheEditorial() {

        User alice = user(2L, "USER");
        givenProblem(EDITORIAL);
        when(contestAccess.mayAccessProblem(7L, alice)).thenReturn(true);
        when(contestAccess.isStaff(alice)).thenReturn(false);
        when(contestAccess.isContested(7L)).thenReturn(false);
        when(problemRepo.existsAcceptedSubmission(7L, 2L)).thenReturn(false);

        assertThat(service.get("two-sum", alice).editorial()).isNull();
    }

    @Test
    void aUserWhoSolvedItGetsTheEditorial() {

        User alice = user(2L, "USER");
        givenProblem(EDITORIAL);
        when(contestAccess.mayAccessProblem(7L, alice)).thenReturn(true);
        when(contestAccess.isStaff(alice)).thenReturn(false);
        when(contestAccess.isContested(7L)).thenReturn(false);
        when(problemRepo.existsAcceptedSubmission(7L, 2L)).thenReturn(true);

        assertThat(service.get("two-sum", alice).editorial()).isEqualTo(EDITORIAL);
    }

    @Test
    void aProblemWithNoEditorialReportsNullRatherThanBlank() {

        User setter = user(1L, "PROBLEM_SETTER");
        givenProblem("   ");
        when(contestAccess.mayAccessProblem(7L, setter)).thenReturn(true);

        assertThat(service.get("two-sum", setter).editorial()).isNull();
        // Nothing to gate, so the gate is not consulted at all.
        verify(contestAccess, never()).isStaff(setter);
    }

    // --- the read path itself ---------------------------------------------------------------

    @Test
    void onlySampleCasesReachTheStatementPage() {

        User alice = user(2L, "USER");
        givenProblem(null);
        when(contestAccess.mayAccessProblem(7L, alice)).thenReturn(true);
        // Built before the when(...) below: creating a mock inside an unfinished stubbing is the
        // UnfinishedStubbing trap this suite has hit before.
        JudgeTestCase sample = testCase(1, "SAMPLE", "2 3", "5");
        JudgeTestCase hidden = testCase(2, "HIDDEN", "100 200", "300");
        when(testCaseRepo.findByProblemIdOrderByOrdAsc(7L)).thenReturn(List.of(sample, hidden));

        ProblemDetail detail = service.get("two-sum", alice);

        assertThat(detail.samples()).hasSize(1);
        assertThat(detail.samples().get(0).input()).isEqualTo("2 3");
        // The hidden case must not appear anywhere in what the page is handed.
        assertThat(detail.samples().toString()).doesNotContain("100 200");
    }

    @Test
    void anEmbargoedProblemIsRefusedWith403() {

        User alice = user(2L, "USER");
        givenProblem(EDITORIAL);
        when(contestAccess.mayAccessProblem(7L, alice)).thenReturn(false);

        Throwable t = catchThrowable(() -> service.get("two-sum", alice));

        assertThat(t).isInstanceOf(ResponseStatusException.class);
        assertThat(((ResponseStatusException) t).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void anUnknownSlugIs404() {

        when(problemRepo.findBySlug("nope")).thenReturn(Optional.empty());

        Throwable t = catchThrowable(() -> service.get("nope", null));

        assertThat(t).isInstanceOf(ResponseStatusException.class);
        assertThat(((ResponseStatusException) t).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // --- FR-15 filter validation --------------------------------------------------------------

    @Test
    void anInvertedDifficultyRangeIsRejectedBeforeItReachesSql() {

        Throwable t = catchThrowable(() -> service.list(null, null, 1500, 800, null, 0, 50));

        assertThat(t).isInstanceOf(ResponseStatusException.class);
        assertThat(((ResponseStatusException) t).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(problemRepo, never())
                .search(org.mockito.ArgumentMatchers.anyBoolean(),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.anyInt(),
                        org.mockito.ArgumentMatchers.anyInt(),
                        org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void theListRejectsOutOfRangePaging() {

        assertThat(((ResponseStatusException) catchThrowable(
                () -> service.list(null, null, null, null, null, -1, 50))).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(((ResponseStatusException) catchThrowable(
                () -> service.list(null, null, null, null, null, 0, 0))).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(((ResponseStatusException) catchThrowable(
                () -> service.list(null, null, null, null, null, 0, 101))).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void moreThanEightTagFiltersIsRejected() {

        List<String> tooMany = List.of("a", "b", "c", "d", "e", "f", "g", "h", "i");

        Throwable t = catchThrowable(() -> service.list(null, null, null, null, tooMany, 0, 50));

        assertThat(t).isInstanceOf(ResponseStatusException.class);
        assertThat(((ResponseStatusException) t).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void aTagFilterCarryingACommaIsRejectedBecauseSqlSplitsOnIt() {

        Throwable t = catchThrowable(() -> service.list(null, null, null, null, List.of("dp,greedy"), 0, 50));

        assertThat(t).isInstanceOf(ResponseStatusException.class);
        assertThat(((ResponseStatusException) t).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // --- fixtures -------------------------------------------------------------------------------

    private void givenProblem(String editorial) {
        ProblemView p = mock(ProblemView.class);
        when(p.getId()).thenReturn(7L);
        when(p.getSlug()).thenReturn("two-sum");
        when(p.getEditorial()).thenReturn(editorial);
        when(problemRepo.findBySlug("two-sum")).thenReturn(Optional.of(p));
    }

    private JudgeTestCase testCase(int ord, String kind, String input, String expected) {
        JudgeTestCase tc = mock(JudgeTestCase.class);
        when(tc.getKind()).thenReturn(kind);
        when(tc.getOrd()).thenReturn(ord);
        when(tc.getInput()).thenReturn(input);
        when(tc.getExpected()).thenReturn(expected);
        return tc;
    }

    private User user(Long id, String role) {
        User u = new User();
        u.setId(id);
        u.setRole(role);
        return u;
    }
}
