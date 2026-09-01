package com.praetor.submission.engine;

import com.praetor.common.event.ContestSubmissionJudgedEvent;
import com.praetor.identity.entity.User;
import com.praetor.identity.repository.UserRepository;
import com.praetor.submission.SubmissionStatus;
import com.praetor.submission.Verdict;
import com.praetor.submission.config.JudgeProperties;
import com.praetor.submission.entity.JudgeProblem;
import com.praetor.submission.entity.JudgeTestCase;
import com.praetor.submission.entity.Submission;
import com.praetor.submission.entity.SubmissionResult;
import com.praetor.submission.repository.JudgeProblemRepository;
import com.praetor.submission.repository.JudgeTestCaseRepository;
import com.praetor.submission.repository.SubmissionRepository;
import com.praetor.submission.repository.SubmissionResultRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The judging orchestrator, with the sandbox mocked out — everything the end-to-end verdict matrix
 * cannot pin down precisely: how many test cases actually run, what is persisted, which faults are
 * a verdict (CE) versus an infra ERROR, and that the work dir is always released.
 *
 * <p>The break-on-first-failure rule matters beyond correctness: it is the reason the judge runs one
 * container per submission rather than batching every input into one run.
 */
class JudgeServiceTest {

    private final SubmissionRepository subRepo = mock(SubmissionRepository.class);
    private final SubmissionResultRepository resultRepo = mock(SubmissionResultRepository.class);
    private final JudgeProblemRepository problemRepo = mock(JudgeProblemRepository.class);
    private final JudgeTestCaseRepository testCaseRepo = mock(JudgeTestCaseRepository.class);
    private final SandboxRunner sandbox = mock(SandboxRunner.class);
    private final SimpMessagingTemplate messaging = mock(SimpMessagingTemplate.class);
    private final UserRepository userRepo = mock(UserRepository.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);

    private final JudgeProperties props =
            new JudgeProperties("img", 2, 256, 64, 2, "/judge", "praetor_work", false, true);

    private final JudgeService service = new JudgeService(
            subRepo, resultRepo, problemRepo, testCaseRepo, sandbox, new VerdictEvaluator(),
            messaging, props, userRepo, events, txManager());

    // --- compile ------------------------------------------------------------------------------

    @Test
    void aCompileErrorIsAVerdictAndNoTestCaseIsEverRun() {

        givenSubmission(1L, null);
        givenProblem();
        givenCases(3);
        when(sandbox.compile(anyString(), any(), any(), any()))
                .thenReturn(new CompileResult(false, "main.cpp:1: error: expected ';'"));

        service.enqueue(1L);

        verify(sandbox, never()).run(anyString(), any(), any(), any());
        Submission saved = lastSavedSubmission();
        assertThat(saved.getStatus()).isEqualTo(SubmissionStatus.DONE);
        assertThat(saved.getVerdict()).isEqualTo(Verdict.CE);
        assertThat(saved.getCompileLog()).contains("expected ';'");
        // CE has no per-case rows to write.
        verify(resultRepo, never()).saveAll(any());
    }

    // --- the test-case loop --------------------------------------------------------------------

    @Test
    void judgingStopsAtTheFirstFailingCase() {

        givenSubmission(1L, null);
        givenProblem();
        givenCases(3);
        givenCompiles();
        // case 1 correct, case 2 wrong; case 3 must never be reached.
        when(sandbox.run(anyString(), any(), any(), any()))
                .thenReturn(runResult("1\n", 10, 1000))
                .thenReturn(runResult("nope\n", 12, 1100))
                .thenReturn(runResult("1\n", 14, 1200));

        service.enqueue(1L);

        verify(sandbox, times(2)).run(anyString(), any(), any(), any());
        assertThat(lastSavedSubmission().getVerdict()).isEqualTo(Verdict.WA);
        assertThat(savedResults()).hasSize(2);
        assertThat(savedResults().get(1).getVerdict()).isEqualTo(Verdict.WA);
    }

    @Test
    void anAllCorrectSubmissionReportsTheWorstTimeAndMemoryAcrossCases() {

        givenSubmission(1L, null);
        givenProblem();
        givenCases(3);
        givenCompiles();
        when(sandbox.run(anyString(), any(), any(), any()))
                .thenReturn(runResult("1\n", 10, 1000))
                .thenReturn(runResult("1\n", 42, 3000))
                .thenReturn(runResult("1\n", 14, 1200));

        service.enqueue(1L);

        Submission saved = lastSavedSubmission();
        assertThat(saved.getVerdict()).isEqualTo(Verdict.AC);
        assertThat(saved.getTimeMs()).isEqualTo(42);
        assertThat(saved.getMemKb()).isEqualTo(3000);
        assertThat(savedResults()).hasSize(3);
    }

    @Test
    void theWorkDirIsReleasedEvenWhenTheSandboxBlowsUp() {

        givenSubmission(1L, null);
        givenProblem();
        givenCases(1);
        givenCompiles();
        when(sandbox.run(anyString(), any(), any(), any()))
                .thenThrow(new SandboxException("docker daemon went away", null));

        service.enqueue(1L);

        verify(sandbox).cleanup(anyString());
        // An infra fault is ERROR, not a verdict the contestant should read as their own.
        assertThat(lastSavedSubmission().getStatus()).isEqualTo(SubmissionStatus.ERROR);
    }

    // --- rejections ----------------------------------------------------------------------------

    @Test
    void anUnsupportedLanguageIsAnErrorRatherThanAVerdict() {

        Submission sub = givenSubmission(1L, null);
        sub.setLanguage("COBOL");
        givenProblem();

        service.enqueue(1L);

        verify(sandbox, never()).compile(anyString(), any(), any(), any());
        assertThat(lastSavedSubmission().getStatus()).isEqualTo(SubmissionStatus.ERROR);
    }

    @Test
    void aCustomCheckerProblemIsRefusedBecauseSpecialIsOutOfScope() {

        givenSubmission(1L, null);
        JudgeProblem p = givenProblem();
        when(p.getJudgeMode()).thenReturn("SPECIAL");

        service.enqueue(1L);

        verify(sandbox, never()).compile(anyString(), any(), any(), any());
        assertThat(lastSavedSubmission().getStatus()).isEqualTo(SubmissionStatus.ERROR);
    }

    @Test
    void aSubmissionThatIsNotQueuedIsLeftAlone() {

        Submission sub = submission(1L, null);
        sub.setStatus(SubmissionStatus.DONE);
        when(subRepo.findById(1L)).thenReturn(Optional.of(sub));

        service.enqueue(1L);

        // The reaper can double-enqueue; claiming must be what stops a second judging pass.
        verify(sandbox, never()).compile(anyString(), any(), any(), any());
        verify(sandbox, never()).run(anyString(), any(), any(), any());
    }

    // --- contest coupling ------------------------------------------------------------------------

    @Test
    void judgingAContestSubmissionAsksForAStandingsRecompute() {

        givenSubmission(1L, 99L);
        givenProblem();
        givenCases(1);
        givenCompiles();
        when(sandbox.run(anyString(), any(), any(), any())).thenReturn(runResult("1\n", 10, 1000));

        service.enqueue(1L);

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(events).publishEvent(captor.capture());
        assertThat(captor.getValue()).isInstanceOf(ContestSubmissionJudgedEvent.class);
        assertThat(((ContestSubmissionJudgedEvent) captor.getValue()).contestId()).isEqualTo(99L);
    }

    @Test
    void judgingAPracticeSubmissionRecomputesNoBoard() {

        givenSubmission(1L, null);
        givenProblem();
        givenCases(1);
        givenCompiles();
        when(sandbox.run(anyString(), any(), any(), any())).thenReturn(runResult("1\n", 10, 1000));

        service.enqueue(1L);

        verify(events, never()).publishEvent(any(ContestSubmissionJudgedEvent.class));
    }

    // --- fixtures ---------------------------------------------------------------------------------

    private PlatformTransactionManager txManager() {
        PlatformTransactionManager tm = mock(PlatformTransactionManager.class);
        when(tm.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
        return tm;
    }

    private Submission submission(Long id, Long contestId) {
        Submission s = new Submission();
        s.setUserId(5L);
        s.setProblemId(7L);
        s.setContestId(contestId);
        s.setLanguage("CPP");
        s.setSourceCode("int main(){}");
        s.setStatus(SubmissionStatus.QUEUED);
        return s;
    }

    private Submission givenSubmission(Long id, Long contestId) {
        Submission s = submission(id, contestId);
        when(subRepo.findById(id)).thenReturn(Optional.of(s));
        when(subRepo.save(any(Submission.class))).thenAnswer(i -> i.getArgument(0));
        User u = new User();
        u.setId(5L);
        u.setUsername("alice");
        when(userRepo.findById(5L)).thenReturn(Optional.of(u));
        return s;
    }

    private JudgeProblem givenProblem() {
        JudgeProblem p = mock(JudgeProblem.class);
        when(p.getId()).thenReturn(7L);
        when(p.getSlug()).thenReturn("two-sum");
        when(p.getJudgeMode()).thenReturn("EXACT");
        when(p.getTimeLimitMs()).thenReturn(1000);
        when(p.getMemLimitKb()).thenReturn(262_144);
        when(problemRepo.findById(7L)).thenReturn(Optional.of(p));
        return p;
    }

    private void givenCases(int n) {
        List<JudgeTestCase> cases = new java.util.ArrayList<>();
        for (int i = 1; i <= n; i++) {
            JudgeTestCase tc = mock(JudgeTestCase.class);
            when(tc.getId()).thenReturn((long) i);
            when(tc.getExpected()).thenReturn("1\n");
            cases.add(tc);
        }
        when(testCaseRepo.findByProblemIdOrderByOrdAsc(7L)).thenReturn(cases);
    }

    private void givenCompiles() {
        when(sandbox.compile(anyString(), any(), any(), any()))
                .thenReturn(new CompileResult(true, ""));
    }

    private RunResult runResult(String stdout, int wallMs, int memKb) {
        return new RunResult(0, stdout, wallMs, memKb, false, false);
    }

    private Submission lastSavedSubmission() {
        ArgumentCaptor<Submission> c = ArgumentCaptor.forClass(Submission.class);
        verify(subRepo, org.mockito.Mockito.atLeastOnce()).save(c.capture());
        return c.getValue();
    }

    @SuppressWarnings("unchecked")
    private List<SubmissionResult> savedResults() {
        ArgumentCaptor<List<SubmissionResult>> c = ArgumentCaptor.forClass(List.class);
        verify(resultRepo).saveAll(c.capture());
        return c.getValue();
    }
}
