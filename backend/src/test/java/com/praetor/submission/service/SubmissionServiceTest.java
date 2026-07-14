package com.praetor.submission.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.praetor.identity.entity.User;
import com.praetor.identity.repository.UserRepository;
import com.praetor.submission.SubmissionStatus;
import com.praetor.submission.Verdict;
import com.praetor.submission.dto.SubmissionResponse;
import com.praetor.submission.engine.JudgeService;
import com.praetor.submission.entity.Submission;
import com.praetor.submission.repository.JudgeProblemRepository;
import com.praetor.submission.repository.ResultView;
import com.praetor.submission.repository.RevealView;
import com.praetor.submission.repository.SubmissionRepository;
import com.praetor.submission.repository.SubmissionResultRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
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

    private final SubmissionService service =
            new SubmissionService(subRepo, resultRepo, problemRepo, userRepo, judgeService);

    private static final long SUB_ID = 42L;
    private static final long OWNER_ID = 7L;

    private User owner() {
        User u = new User();
        u.setId(OWNER_ID);
        u.setRole("USER");
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
}
