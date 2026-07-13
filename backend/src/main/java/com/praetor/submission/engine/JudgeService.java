package com.praetor.submission.engine;

import com.praetor.identity.entity.User;
import com.praetor.identity.repository.UserRepository;
import com.praetor.submission.SubmissionStatus;
import com.praetor.submission.Verdict;
import com.praetor.submission.config.JudgeProperties;
import com.praetor.submission.dto.SubmissionStatusEvent;
import com.praetor.submission.engine.checker.Checker;
import com.praetor.submission.engine.checker.Checkers;
import com.praetor.submission.entity.JudgeProblem;
import com.praetor.submission.entity.JudgeTestCase;
import com.praetor.submission.entity.Submission;
import com.praetor.submission.entity.SubmissionResult;
import com.praetor.submission.repository.JudgeProblemRepository;
import com.praetor.submission.repository.JudgeTestCaseRepository;
import com.praetor.submission.repository.SubmissionRepository;
import com.praetor.submission.repository.SubmissionResultRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Async judging orchestrator: drives one submission through QUEUED→JUDGING→DONE|ERROR, running each
 * test case through the {@link SandboxRunner} + {@link VerdictEvaluator}, persisting results, and
 * pushing every status transition over WebSocket.
 *
 * <p>Deliberately NOT {@code @Transactional} as a whole — each DB write is its own short tx via
 * {@link TransactionTemplate}, so no transaction is held open across the multi-second sandbox runs.
 * CE is a verdict (status DONE); only infra faults set status ERROR.
 */
@Service
public class JudgeService {

    private static final Logger log = LoggerFactory.getLogger(JudgeService.class);

    /** Byte ceiling on the captured actual output stored for the reveal (separate from the sandbox
     * stdout cap). Keeps a flood-output submission from bloating the row / response. */
    private static final int DISPLAY_CAP = 4096;

    private final SubmissionRepository subRepo;
    private final SubmissionResultRepository resultRepo;
    private final JudgeProblemRepository problemRepo;
    private final JudgeTestCaseRepository testCaseRepo;
    private final SandboxRunner sandbox;
    private final VerdictEvaluator evaluator;
    private final SimpMessagingTemplate messaging;
    private final JudgeProperties props;
    private final UserRepository userRepo;
    private final TransactionTemplate tx;

    public JudgeService(SubmissionRepository subRepo, SubmissionResultRepository resultRepo,
                        JudgeProblemRepository problemRepo, JudgeTestCaseRepository testCaseRepo,
                        SandboxRunner sandbox, VerdictEvaluator evaluator,
                        SimpMessagingTemplate messaging, JudgeProperties props,
                        UserRepository userRepo, PlatformTransactionManager txManager) {
        this.subRepo = subRepo;
        this.resultRepo = resultRepo;
        this.problemRepo = problemRepo;
        this.testCaseRepo = testCaseRepo;
        this.sandbox = sandbox;
        this.evaluator = evaluator;
        this.messaging = messaging;
        this.props = props;
        this.userRepo = userRepo;
        this.tx = new TransactionTemplate(txManager);
    }

    /** Entry point — runs on the judge pool. Never lets an exception escape the pool thread. */
    @Async("judgeExecutor")
    public void enqueue(Long submissionId) {
        try {
            judge(submissionId);
        } catch (Exception e) {
            log.error("judge failed for submission {}", submissionId, e);
            markError(submissionId);
            pushCurrent(submissionId);
        }
    }

    private void judge(Long id) {
        if (!claim(id)) {
            return; // not QUEUED — already judged or being judged (reaper double-enqueue guard)
        }
        String owner = ownerUsername(id);
        push(owner, id, SubmissionStatus.JUDGING, null);

        Submission sub = subRepo.findById(id).orElseThrow();
        JudgeProblem problem = problemRepo.findById(sub.getProblemId()).orElse(null);
        if (problem == null) {
            markError(id);
            push(owner, id, SubmissionStatus.ERROR, null);
            return;
        }
        Checker checker = Checkers.of(problem.getJudgeMode(), problem.getFloatEps());
        if (checker == null) {
            // SPECIAL (custom checker) or an unknown mode — not supported.
            log.warn("submission {} problem {} judge_mode {} not supported", id,
                    problem.getSlug(), problem.getJudgeMode());
            markError(id);
            push(owner, id, SubmissionStatus.ERROR, null);
            return;
        }

        Language language = Language.from(sub.getLanguage());
        if (language == null) {
            log.warn("submission {} unsupported language {}", id, sub.getLanguage());
            markError(id);
            push(owner, id, SubmissionStatus.ERROR, null);
            return;
        }

        List<JudgeTestCase> tests = testCaseRepo.findByProblemIdOrderByOrdAsc(problem.getId());
        RunLimits limits = buildLimits(problem, language);
        String runId = id + "-" + UUID.randomUUID();
        try {
            CompileResult cr = sandbox.compile(runId, language, sub.getSourceCode());
            if (!cr.success()) {
                markDone(id, Verdict.CE, null, null, cr.log(), List.of());
                push(owner, id, SubmissionStatus.DONE, Verdict.CE);
                return;
            }

            List<SubmissionResult> results = new ArrayList<>();
            String overall = Verdict.AC;
            int maxTime = 0;
            int maxMem = 0;
            for (JudgeTestCase tc : tests) {
                RunResult rr = sandbox.run(runId, language, tc, limits);
                String v = evaluator.evaluate(rr, tc.getExpected(), limits, checker);
                maxTime = Math.max(maxTime, rr.wallMs());
                if (rr.memKb() != null) {
                    maxMem = Math.max(maxMem, rr.memKb());
                }
                if (!Verdict.AC.equals(v)) {
                    // First non-AC decides the overall verdict. Capture its stdout (truncated) so
                    // the practice-mode reveal can show it; the read path gates who ever sees it.
                    results.add(new SubmissionResult(id, tc.getId(), v, rr.wallMs(), rr.memKb(),
                            truncate(rr.stdout(), DISPLAY_CAP)));
                    overall = v;
                    break;
                }
                results.add(new SubmissionResult(id, tc.getId(), v, rr.wallMs(), rr.memKb()));
            }
            markDone(id, overall, maxTime, maxMem, "", results);
            push(owner, id, SubmissionStatus.DONE, overall);
        } finally {
            sandbox.cleanup(runId);
        }
    }

    /** Atomically claim a QUEUED submission → JUDGING. Returns false if it wasn't QUEUED. */
    private boolean claim(Long id) {
        return Boolean.TRUE.equals(tx.execute(s -> {
            Submission sub = subRepo.findById(id).orElse(null);
            if (sub == null || !SubmissionStatus.QUEUED.equals(sub.getStatus())) {
                return false;
            }
            sub.setStatus(SubmissionStatus.JUDGING);
            subRepo.save(sub);
            return true;
        }));
    }

    private void markError(Long id) {
        tx.executeWithoutResult(s -> {
            Submission sub = subRepo.findById(id).orElse(null);
            if (sub == null) {
                return;
            }
            sub.setStatus(SubmissionStatus.ERROR);
            sub.setCompileLog("internal judge error");
            subRepo.save(sub);
        });
    }

    private void markDone(Long id, String verdict, Integer timeMs, Integer memKb,
                          String compileLog, List<SubmissionResult> results) {
        tx.executeWithoutResult(s -> {
            if (!results.isEmpty()) {
                resultRepo.saveAll(results);
            }
            Submission sub = subRepo.findById(id).orElse(null);
            if (sub == null) {
                return;
            }
            sub.setStatus(SubmissionStatus.DONE);
            sub.setVerdict(verdict);
            sub.setTimeMs(timeMs);
            sub.setMemKb(memKb);
            if (compileLog != null) {
                sub.setCompileLog(compileLog);
            }
            subRepo.save(sub);
        });
    }

    /** Null-tolerant prefix truncation ({@code rr.stdout()} is null for TLE/RE). */
    private static String truncate(String s, int cap) {
        if (s == null) {
            return null;
        }
        return s.length() <= cap ? s : s.substring(0, cap);
    }

    private RunLimits buildLimits(JudgeProblem p, Language language) {
        double t = language.timeMultiplier();
        double m = language.memMultiplier();
        int baseSoft = p.getTimeLimitMs() != null ? p.getTimeLimitMs() : 1000;
        int baseMemKb = p.getMemLimitKb() != null ? p.getMemLimitKb() : 262_144;
        int soft = (int) Math.round(baseSoft * t);
        int hard = (int) Math.round(props.cpuSeconds() * 1000 * t);
        int memLimitKb = (int) Math.round(baseMemKb * m);
        int memMb = (int) Math.round(props.memMb() * m);
        return new RunLimits(soft, hard, memMb, props.pidsMax(), memLimitKb);
    }

    private String ownerUsername(Long id) {
        Submission sub = subRepo.findById(id).orElse(null);
        if (sub == null) {
            return null;
        }
        return userRepo.findById(sub.getUserId()).map(User::getUsername).orElse(null);
    }

    private void pushCurrent(Long id) {
        Submission sub = subRepo.findById(id).orElse(null);
        if (sub != null) {
            push(ownerUsername(id), id, sub.getStatus(), sub.getVerdict());
        }
    }

    private void push(String username, Long id, String status, String verdict) {
        if (username == null) {
            return;
        }
        messaging.convertAndSendToUser(username, "/queue/submission/" + id,
                new SubmissionStatusEvent(id, status, verdict));
    }
}
