package com.praetor.identity.service;

import com.praetor.identity.repository.RatedContestRepository;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Applies Elo to contests once they finish.
 *
 * <p>Lives in the identity module, not in {@code ContestService}, so the dependency runs one
 * way: rating reads the contest module's standings, and the contest module knows nothing about
 * rating. It also keeps a scheduled job out of a request-path service.
 *
 * <p>Each contest is rated in its own transaction — {@link RatingService#applyContestResults}
 * is {@code @Transactional}, and is called through the bean here so the proxy applies. One
 * contest failing (a standings row whose user has since vanished, say) must not roll back the
 * contests already rated in the same tick, nor block the ones after it, nor wedge the job into
 * failing forever on every tick.
 */
@Component
public class ContestRatingScheduler {

    private static final Logger log = LoggerFactory.getLogger(ContestRatingScheduler.class);

    private final RatedContestRepository ratedContestRepo;
    private final RatingService ratingService;

    public ContestRatingScheduler(RatedContestRepository ratedContestRepo,
                                  RatingService ratingService) {
        this.ratedContestRepo = ratedContestRepo;
        this.ratingService = ratingService;
    }

    @Scheduled(fixedDelayString = "${praetor.rating.contest-scan-ms:60000}")
    public void applyPendingContestRatings() {
        List<Long> pending = ratedContestRepo.findContestIdsAwaitingRating();
        if (pending.isEmpty()) {
            return;
        }

        log.info("Rating {} finished contest(s): {}", pending.size(), pending);
        for (Long contestId : pending) {
            try {
                ratingService.applyContestResults(contestId);
            } catch (RuntimeException e) {
                // Logged, not rethrown: the next contest in this tick still gets its ratings.
                log.error("Rating contest {} failed; other contests unaffected", contestId, e);
            }
        }
    }
}
