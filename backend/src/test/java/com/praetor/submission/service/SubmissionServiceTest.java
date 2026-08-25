package com.praetor.submission.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.praetor.contest.service.ContestAccessService;
import com.praetor.identity.entity.User;
import com.praetor.identity.repository.UserRepository;
import com.praetor.submission.SubmissionStatus;
import com.praetor.submission.Verdict;
import com.praetor.submission.dto.SubmissionPage;
import com.praetor.submission.dto.SubmissionResponse;
import com.praetor.submission.dto.SubmissionSummary;
import com.praetor.submission.dto.SubmitRequest;
import com.praetor.submission.engine.JudgeService;
import com.praetor.submission.entity.JudgeProblem;
import com.praetor.submission.entity.Submission;
import com.praetor.submission.repository.JudgeProblemRepository;
import com.praetor.submission.repository.ResultView;
import com.praetor.submission.repository.RevealView;
import com.praetor.submission.repository.SubmissionRepository;
import com.praetor.submission.repository.SubmissionResultRepository;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.server.ResponseStatusException;

/**
 * The feat-3d practice-reveal SECURITY GATE. Verifies that the first-failing test case's
 * input/expected/actual is surfaced ONLY for a fully-judged practice (non-contest) submission with a
 * real failing verdict, and that the reveal query is never even issued otherwise. Pure Mockito — no
 * DB (the JPQL itself is exercised end-to-end by the curl verification).
 */
class SubmissionServiceTest {

    private final SubmissionRepository subRepo = mock(SubmissionRepository.class);
    private final SubmissionResultRepository resultRepo = mock(SubmissionResultRepository.class);
    private final JudgeProblemRepository problemRepo = mock(JudgeProblemRepository.class);
    private final UserRepository userRepo = mock(UserRepository.class);
    private final JudgeService judgeService = mock(JudgeService.class);
    // A no-op mock manager is enough — TransactionTemplate still runs the callback (getTransaction
    // returns a null status, commit is a no-op), which is all the rejudge tests need.
    private final PlatformTransactionManager txManager = mock(PlatformTransactionManager.class);
    private final SubmissionRateLimiter rateLimiter = mock(SubmissionRateLimiter.class);

    private final ContestAccessService contestAccess = mock(ContestAccessService.class);

    private final SubmissionService service =
            new SubmissionService(subRepo, resultRepo, problemRepo, userRepo, judgeService,
                    rateLimiter, contestAccess, txManager);

    private static final long SUB_ID = 42L;
    private static final long OWNER_ID = 7L;

    /**
     * Default the embargo open, so the tests that predate it read the same as before; the two
     * embargo tests stub it shut for themselves. A mock would otherwise answer false and 403 every
     * create.
     */
    @BeforeEach
    void embargoOpenByDefault() {
        when(contestAccess.mayAccessProblem(anyLong(), any())).thenReturn(true);
    }

    private User owner() {
        User u = new User();
        u.setId(OWNER_ID);
        u.setRole("USER");
        return u;
    }

    private User admin() {
        User u = new User();
        u.setId(1L);
        u.setRole("ADMIN");
        return u;
    }

    private Submission submission(Long contestId, String status, String verdict) {
        Submission s = new Submission();
        s.setUserId(OWNER_ID);
        s.setProblemId(1L);
        s.setContestId(contestId);
        s.setStatus(status);
        s.setVerdict(verdict);
        return s;
    }

    private ResultView resultView(int ord, String verdict) {
        ResultView v = mock(ResultView.class);
        when(v.getOrd()).thenReturn(ord);
        when(v.getVerdict()).thenReturn(verdict);
        when(v.getTimeMs()).thenReturn(10);
        when(v.getMemKb()).thenReturn(2000);
        return v;
    }

    private RevealView revealView(int ord) {
        RevealView v = mock(RevealView.class);
        when(v.getOrd()).thenReturn(ord);
        when(v.getInput()).thenReturn("IN");
        when(v.getExpected()).thenReturn("EXP");
        when(v.getActualOutput()).thenReturn("ACT");
        return v;
    }

    @Test
    void practiceWa_revealsFirstFailingRowOnly() {
        // Build the projection mocks BEFORE the outer when() — creating them inside .thenReturn(...)
        // starts a nested when() and Mockito flags "unfinished stubbing".
        ResultView acView = resultView(1, Verdict.AC);
        ResultView waView = resultView(2, Verdict.WA);
        RevealView reveal = revealView(2);
        when(subRepo.findById(SUB_ID))
                .thenReturn(Optional.of(submission(null, SubmissionStatus.DONE, Verdict.WA)));
        when(resultRepo.findResultViews(SUB_ID)).thenReturn(List.of(acView, waView));
        when(resultRepo.findFailingReveal(SUB_ID)).thenReturn(List.of(reveal));

        SubmissionResponse resp = service.get(SUB_ID, owner());

        assertThat(resp.practice()).isTrue();
        var acRow = resp.results().get(0);
        var waRow = resp.results().get(1);
        assertThat(acRow.input()).isNull();
        assertThat(acRow.expected()).isNull();
        assertThat(acRow.actualOutput()).isNull();
        assertThat(waRow.input()).isEqualTo("IN");
        assertThat(waRow.expected()).isEqualTo("EXP");
        assertThat(waRow.actualOutput()).isEqualTo("ACT");
        verify(resultRepo).findFailingReveal(SUB_ID);
    }

    @Test
    void contestWa_neverReveals() {
        ResultView waView = resultView(1, Verdict.WA);
        when(subRepo.findById(SUB_ID))
                .thenReturn(Optional.of(submission(99L, SubmissionStatus.DONE, Verdict.WA)));
        when(resultRepo.findResultViews(SUB_ID)).thenReturn(List.of(waView));

        SubmissionResponse resp = service.get(SUB_ID, owner());

        assertThat(resp.practice()).isFalse();
        assertThat(resp.results().get(0).input()).isNull();
        verify(resultRepo, never()).findFailingReveal(anyLong());
    }

    @Test
    void practiceAc_neverReveals() {
        ResultView acView = resultView(1, Verdict.AC);
        when(subRepo.findById(SUB_ID))
                .thenReturn(Optional.of(submission(null, SubmissionStatus.DONE, Verdict.AC)));
        when(resultRepo.findResultViews(SUB_ID)).thenReturn(List.of(acView));

        SubmissionResponse resp = service.get(SUB_ID, owner());

        assertThat(resp.practice()).isTrue();
        assertThat(resp.results().get(0).input()).isNull();
        verify(resultRepo, never()).findFailingReveal(anyLong());
    }

    @Test
    void practiceCe_neverReveals() {
        when(subRepo.findById(SUB_ID))
                .thenReturn(Optional.of(submission(null, SubmissionStatus.DONE, Verdict.CE)));
        when(resultRepo.findResultViews(SUB_ID)).thenReturn(List.of());

        service.get(SUB_ID, owner());

        verify(resultRepo, never()).findFailingReveal(anyLong());
    }

    @Test
    void stillJudging_neverReveals() {
        when(subRepo.findById(SUB_ID))
                .thenReturn(Optional.of(submission(null, SubmissionStatus.JUDGING, null)));
        when(resultRepo.findResultViews(SUB_ID)).thenReturn(List.of());

        service.get(SUB_ID, owner());

        verify(resultRepo, never()).findFailingReveal(anyLong());
    }

    @Test
    void nonOwner_404_andNoResultQueries() {
        when(subRepo.findById(SUB_ID))
                .thenReturn(Optional.of(submission(null, SubmissionStatus.DONE, Verdict.WA)));
        User stranger = new User();
        stranger.setId(999L);
        stranger.setRole("USER");

        assertThatThrownBy(() -> service.get(SUB_ID, stranger))
                .isInstanceOf(ResponseStatusException.class);
        verify(resultRepo, never()).findResultViews(anyLong());
        verify(resultRepo, never()).findFailingReveal(anyLong());
    }

    // ---- rate limit (FR-17) ----

    @Test
    void create_unsupportedLanguage_doesNotSpendTheCooldown() {
        assertThatThrownBy(() -> service.create(
                new SubmitRequest("a-plus-b", null, "COBOL", "x"), owner()))
                .isInstanceOf(ResponseStatusException.class);

        verifyNoInteractions(rateLimiter);
        verify(subRepo, never()).save(any());
    }

    @Test
    void create_unknownProblem_doesNotSpendTheCooldown() {
        when(problemRepo.findBySlug("no-such")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(
                new SubmitRequest("no-such", null, "CPP", "x"), owner()))
                .isInstanceOf(ResponseStatusException.class);

        // the whole point of moving this out of the filter: a 404 must cost the user nothing
        verifyNoInteractions(rateLimiter);
        verify(subRepo, never()).save(any());
    }

    @Test
    void create_rateLimited_neverReachesTheJudge() {
        JudgeProblem problem = mock(JudgeProblem.class);
        when(problem.getId()).thenReturn(7L);
        when(problemRepo.findBySlug("a-plus-b")).thenReturn(Optional.of(problem));

        doThrow(new SubmissionRateLimitedException(6))
                .when(rateLimiter).recordOrReject(OWNER_ID);

        assertThatThrownBy(() -> service.create(
                new SubmitRequest("a-plus-b", null, "CPP", "int main(){}"), owner()))
                .isInstanceOf(SubmissionRateLimitedException.class);

        verify(subRepo, never()).save(any());
        verify(judgeService, never()).enqueue(anyLong());
    }

    @Test
    void create_embargoedProblem_403_andDoesNotSpendTheCooldown() {
        JudgeProblem problem = mock(JudgeProblem.class);
        when(problem.getId()).thenReturn(7L);
        when(problemRepo.findBySlug("a-plus-b")).thenReturn(Optional.of(problem));
        when(contestAccess.mayAccessProblem(eq(7L), any())).thenReturn(false);

        assertThatThrownBy(() -> service.create(
                new SubmitRequest("a-plus-b", null, "CPP", "int main(){}"), owner()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("403");

        // refused before the cooldown, like the 404 above — a wall costs nothing
        verifyNoInteractions(rateLimiter);
        verify(subRepo, never()).save(any());
        verify(judgeService, never()).enqueue(anyLong());
    }

    // ---- rejudge (FR-27) ----

    @Test
    void rejudge_nonAdmin_403_andNoWork() {
        assertThatThrownBy(() -> service.rejudge(SUB_ID, owner()))
                .isInstanceOf(ResponseStatusException.class);
        verify(subRepo, never()).findById(anyLong());
        verify(resultRepo, never()).deleteBySubmissionId(anyLong());
        verify(judgeService, never()).enqueue(anyLong());
    }

    @Test
    void rejudge_missingSubmission_404_andNoEnqueue() {
        when(subRepo.findById(SUB_ID)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.rejudge(SUB_ID, admin()))
                .isInstanceOf(ResponseStatusException.class);
        verify(judgeService, never()).enqueue(anyLong());
    }

    @Test
    void rejudge_admin_clearsResultsResetsAndReenqueues() {
        Submission sub = submission(9L, SubmissionStatus.DONE, Verdict.WA);
        sub.setTimeMs(120);
        sub.setMemKb(4000);
        sub.setCompileLog("prev");
        when(subRepo.findById(SUB_ID)).thenReturn(Optional.of(sub));

        var resp = service.rejudge(SUB_ID, admin());

        assertThat(resp.id()).isEqualTo(SUB_ID);
        assertThat(resp.status()).isEqualTo(SubmissionStatus.QUEUED);
        // prior per-testcase rows deleted (unique-constraint + stale reveal), submission reset
        verify(resultRepo).deleteBySubmissionId(SUB_ID);
        verify(subRepo).save(sub);
        assertThat(sub.getStatus()).isEqualTo(SubmissionStatus.QUEUED);
        assertThat(sub.getVerdict()).isNull();
        assertThat(sub.getTimeMs()).isNull();
        assertThat(sub.getMemKb()).isNull();
        assertThat(sub.getCompileLog()).isNull();
        // re-handed to the judge AFTER the reset
        verify(judgeService).enqueue(SUB_ID);
    }

    @Test
    void practiceWa_problemInLiveContest_neverReveals() {
        // Live-contest guard: even a practice WA must not reveal hidden tests for a problem that is
        // currently used by a live contest.
        ResultView acView = resultView(1, Verdict.AC);
        ResultView waView = resultView(2, Verdict.WA);
        when(subRepo.findById(SUB_ID))
                .thenReturn(Optional.of(submission(null, SubmissionStatus.DONE, Verdict.WA)));
        when(resultRepo.findResultViews(SUB_ID)).thenReturn(List.of(acView, waView));
        when(subRepo.existsLiveContestForProblem(anyLong())).thenReturn(true);

        SubmissionResponse resp = service.get(SUB_ID, owner());

        assertThat(resp.practice()).isTrue();
        assertThat(resp.results()).allSatisfy(r -> {
            assertThat(r.input()).isNull();
            assertThat(r.expected()).isNull();
            assertThat(r.actualOutput()).isNull();
        });
        verify(resultRepo, never()).findFailingReveal(anyLong());
    }

    @Test
    void history_ownerGetsOwnPage() {
        User viewer = owner();
        viewer.setUsername("alice");
        SubmissionSummary summary = new SubmissionSummary(
                10L, "alice", "a-plus-b", "CPP", SubmissionStatus.DONE, Verdict.AC, 100,
                ZonedDateTime.parse("2026-07-12T10:00:00Z"));
        when(subRepo.findHistoryPage(eq(OWNER_ID), eq(7L), eq(88L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(summary), PageRequest.of(0, 20), 1));

        SubmissionPage page = service.history(viewer, null, 7L, 88L, 0, 20);

        assertThat(page.content()).hasSize(1);
        assertThat(page.content().get(0).problemSlug()).isEqualTo("a-plus-b");
        assertThat(page.totalElements()).isEqualTo(1L);
        assertThat(page.page()).isZero();
    }

    @Test
    void history_adminCanViewAnotherUserPage() {
        User adminUser = admin();
        adminUser.setUsername("admin");
        User other = new User();
        other.setId(99L);
        other.setUsername("bob");
        other.setRole("USER");
        SubmissionSummary summary = new SubmissionSummary(
                11L, "bob", "two-sum", "PYTHON", SubmissionStatus.DONE, Verdict.WA, 80,
                ZonedDateTime.parse("2026-07-13T09:30:00Z"));
        when(userRepo.findByUsername("bob")).thenReturn(Optional.of(other));
        when(subRepo.findHistoryPage(eq(99L), eq(12L), eq(1L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(summary), PageRequest.of(1, 10), 1));

        SubmissionPage page = service.history(adminUser, "bob", 12L, 1L, 1, 10);

        assertThat(page.content()).singleElement().extracting(SubmissionSummary::problemSlug)
                .isEqualTo("two-sum");
        assertThat(page.page()).isEqualTo(1);
        assertThat(page.size()).isEqualTo(10);
    }

    @Test
    void history_nonAdminCannotViewAnotherUserPage() {
        User other = new User();
        other.setId(99L);
        other.setUsername("bob");
        other.setRole("USER");
        when(userRepo.findByUsername("bob")).thenReturn(Optional.of(other));

        assertThatThrownBy(() -> service.history(owner(), "bob", null, null, 0, 20))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("only view your own submission history");
        verify(subRepo, never()).findHistoryPage(anyLong(), any(), any(), any(Pageable.class));
    }

    @Test
    void history_rejectsNegativePage() {
        assertThatThrownBy(() -> service.history(owner(), null, null, null, -1, 20))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("page must be >= 0");
    }

    private User problemSetter() {
        User u = new User();
        u.setId(3L);
        u.setUsername("setter01");
        u.setRole("PROBLEM_SETTER");
        return u;
    }

    /**
     * The contract calls this endpoint owner/staff, and PROBLEM_SETTER is staff everywhere else in
     * the product — it already sees through a standings freeze. Testing for ADMIN by name locked it
     * out, which is the same mistake ProblemAuthz exists to prevent.
     */
    @Test
    void history_problemSetterCountsAsStaffAndMayViewAnotherUser() {
        User other = new User();
        other.setId(99L);
        other.setUsername("bob");
        other.setRole("USER");
        when(userRepo.findByUsername("bob")).thenReturn(Optional.of(other));
        when(subRepo.findHistoryPage(eq(99L), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        SubmissionPage page = service.history(problemSetter(), "bob", null, null, 0, 20);

        assertThat(page.totalElements()).isZero();
        verify(subRepo).findHistoryPage(eq(99L), any(), any(), any(Pageable.class));
    }

    @Test
    void history_problemSetterWithNoHandleIsNotPinnedToTheirOwnRows() {
        when(subRepo.findHistoryPage(eq(null), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        service.history(problemSetter(), null, null, null, 0, 20);

        // Null userId = every user's rows, which is what staff filtering means here.
        verify(subRepo).findHistoryPage(eq(null), any(), any(), any(Pageable.class));
    }

    /**
     * Authorising after the lookup would answer 404 for an unknown handle and 403 for a real one,
     * handing out an existence oracle. The repository must not be touched at all.
     */
    @Test
    void history_foreignHandleIsRefusedWithoutLookingTheHandleUp() {
        assertThatThrownBy(() -> service.history(owner(), "bob", null, null, 0, 20))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("only view your own submission history");

        verify(userRepo, never()).findByUsername(any());
        verify(subRepo, never()).findHistoryPage(anyLong(), any(), any(), any(Pageable.class));
    }

    @Test
    void history_ownHandleSpelledOutIsAllowed() {
        User viewer = owner();
        viewer.setUsername("alice");
        when(userRepo.findByUsername("alice")).thenReturn(Optional.of(viewer));
        when(subRepo.findHistoryPage(eq(OWNER_ID), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        SubmissionPage page = service.history(viewer, "alice", null, null, 0, 20);

        assertThat(page.page()).isZero();
    }

    @Test
    void history_rejectsSizeBelowOne() {
        assertThatThrownBy(() -> service.history(owner(), null, null, null, 0, 0))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("size must be between 1 and 100");
    }

    @Test
    void history_rejectsSizeAboveTheCap() {
        assertThatThrownBy(() -> service.history(owner(), null, null, null, 0, 101))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("size must be between 1 and 100");
    }
}
