package com.praetor.submission.engine;

import com.praetor.submission.SubmissionStatus;
import com.praetor.submission.entity.Submission;
import com.praetor.submission.repository.SubmissionRepository;
import java.time.ZonedDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Durability backstop for the in-memory judge pool. Every 30s:
 * <ul>
 *   <li>re-enqueue submissions stuck in QUEUED past a grace window — covers tasks rejected by the
 *       bounded queue AND recovers work orphaned by a backend restart (crash recovery);</li>
 *   <li>fail submissions stuck in JUDGING far past any reasonable judge time → ERROR.</li>
 * </ul>
 * Runs through the Spring proxy (so {@code @Transactional} applies). Stuck-detection is by
 * {@code created_at} (the schema has no judging-started column) — coarse but safe with the generous
 * JUDGING window.
 */
@Component
public class JudgeReaper {

    private static final Logger log = LoggerFactory.getLogger(JudgeReaper.class);

    /** A QUEUED row older than this was likely rejected/orphaned — re-enqueue it. */
    private static final int QUEUED_GRACE_SECONDS = 30;
    /** A JUDGING row older than this is presumed dead → ERROR. Must exceed max judge time. */
    private static final int JUDGING_TIMEOUT_MINUTES = 5;

    private final SubmissionRepository subRepo;
    private final JudgeService judgeService;

    public JudgeReaper(SubmissionRepository subRepo, JudgeService judgeService) {
        this.subRepo = subRepo;
        this.judgeService = judgeService;
    }

    @Scheduled(fixedDelay = 30_000)
    @Transactional
    public void reap() {
        ZonedDateTime now = ZonedDateTime.now();

        List<Submission> stuckQueued = subRepo.findByStatusAndCreatedAtBefore(
                SubmissionStatus.QUEUED, now.minusSeconds(QUEUED_GRACE_SECONDS));
        for (Submission s : stuckQueued) {
            log.warn("reaper re-enqueuing stuck QUEUED submission {}", s.getId());
            judgeService.enqueue(s.getId());
        }

        List<Submission> stuckJudging = subRepo.findByStatusAndCreatedAtBefore(
                SubmissionStatus.JUDGING, now.minusMinutes(JUDGING_TIMEOUT_MINUTES));
        for (Submission s : stuckJudging) {
            log.warn("reaper failing stuck JUDGING submission {}", s.getId());
            s.setStatus(SubmissionStatus.ERROR);
            s.setCompileLog("judge timed out (reaper)");
            subRepo.save(s);
        }
    }
}
