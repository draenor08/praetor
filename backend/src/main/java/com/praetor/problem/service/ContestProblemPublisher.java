package com.praetor.problem.service;

import com.praetor.problem.entity.Problem;
import com.praetor.problem.repository.ProblemRepository;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Releases a contest's problems into public practice once the contest is over.
 *
 * <p>A contest problem is authored as a draft — archived and unpublished — so that nobody can read
 * it beforehand. Once the contest ends there is nothing left to protect, and the contest embargo
 * has already stopped withholding it; without this sweep it would stay archived forever and never
 * reach the practice list, which is the opposite of what a finished round should leave behind.
 *
 * <p>Lives in the problem module because it writes problems. It reads contest end times through a
 * native query rather than the contest module's entities, keeping the dependency one way.
 *
 * <p>Naturally idempotent: publishing clears the very condition the query selects on, so a repeated
 * tick finds nothing. Each problem is flipped independently so one bad row cannot strand the rest.
 */
@Component
public class ContestProblemPublisher {

    private static final Logger log = LoggerFactory.getLogger(ContestProblemPublisher.class);

    private final ProblemRepository problemRepository;

    public ContestProblemPublisher(ProblemRepository problemRepository) {
        this.problemRepository = problemRepository;
    }

    @Scheduled(fixedDelayString = "${praetor.problem.publish-scan-ms:60000}")
    @Transactional
    public void publishProblemsOfEndedContests() {
        List<Problem> pending = problemRepository.findUnpublishedFromEndedContests();
        if (pending.isEmpty()) {
            return;
        }

        for (Problem problem : pending) {
            problem.setArchived(false);
            problem.publish();
        }
        problemRepository.saveAll(pending);

        log.info("Published {} problem(s) from finished contests: {}",
                pending.size(), pending.stream().map(Problem::getSlug).toList());
    }
}
