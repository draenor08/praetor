package com.praetor.submission.engine;

import com.praetor.identity.entity.User;
import com.praetor.identity.repository.UserRepository;
import com.praetor.submission.SubmissionStatus;
import com.praetor.submission.Verdict;
import com.praetor.submission.config.JudgeProperties;
import com.praetor.submission.dto.SubmissionStatusEvent;
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
        if (!"EXACT".equals(problem.getJudgeMode())) {
            log.warn("submission {} problem {} judge_mode {} not supported yet", id,
                    problem.getSlug(), problem.getJudgeMode());
            markError(id);
            push(owner, id, SubmissionStatus.ERROR, null);
            return;
        }

        List<JudgeTestCase> tests = testCaseRepo.findByProblemIdOrderByOrdAsc(problem.getId());
        RunLimits limits = buildLimits(problem);
        String runId = id + "-" + UUID.randomUUID();
        try {
            CompileResult cr = sandbox.compile(runId, sub.getSourceCode());
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
                RunResult rr = sandbox.run(runId, tc, limits);
                String v = evaluator.evaluate(rr, tc.getExpected(), limits);
                results.add(new SubmissionResult(id, tc.getId(), v, rr.wallMs(), rr.memKb()));
                maxTime = Math.max(maxTime, rr.wallMs());
                if (rr.memKb() != null) {
                    maxMem = Math.max(maxMem, rr.memKb());
                }
                if (!Verdict.AC.equals(v)) {
                    overall = v; // first non-AC decides the overall verdict
                    break;
                }
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

    private RunLimits buildLimits(JudgeProblem p) {
        int soft = p.getTimeLimitMs() != null ? p.getTimeLimitMs() : 1000;
        int hard = props.cpuSeconds() * 1000;
        int memLimitKb = p.getMemLimitKb() != null ? p.getMemLimitKb() : 262_144;
        return new RunLimits(soft, hard, props.memMb(), props.pidsMax(), memLimitKb);
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
