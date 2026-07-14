package com.praetor.contest.standings;

import com.praetor.common.event.ContestSubmissionJudgedEvent;
import com.praetor.contest.dto.StandingsResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Recomputes and publishes a contest's standings when one of its submissions is judged. Runs on the
 * dedicated single-thread {@code contestExecutor} so recomputes serialise (never overlap → no board
 * race) and never block the judge pool.
 *
 * <p><b>Freeze-aware, single branch.</b> The board computed for the non-privileged viewer is always
 * what goes to the shared topic — that is the frozen board while a freeze is active, the live board
 * otherwise. The LIVE board is computed and sent ONLY to privileged subscribers over their user
 * queue, and ONLY during a freeze. The live board therefore never reaches the topic during a freeze
 * — the invariant that keeps post-freeze results from leaking to contestants.
 *
 * <p>Every push is a whole board (full recompute), so a missed intermediate push is harmless: the
 * next judged submission delivers the complete current state.
 */
@Component
public class StandingsPublisher {

    private static final Logger log = LoggerFactory.getLogger(StandingsPublisher.class);

    private final StandingsService standingsService;
    private final PrivilegedSubscriberRegistry registry;
    private final SimpMessagingTemplate messaging;

    public StandingsPublisher(StandingsService standingsService, PrivilegedSubscriberRegistry registry,
                              SimpMessagingTemplate messaging) {
        this.standingsService = standingsService;
        this.registry = registry;
        this.messaging = messaging;
    }

    @Async("contestExecutor")
    @EventListener
    public void onContestSubmissionJudged(ContestSubmissionJudgedEvent event) {
        publish(event.contestId());
    }

    /** Recompute + publish. Package-visible so it can be driven directly in tests. */
    void publish(Long contestId) {
        StandingsResponse broadcast = standingsService.snapshot(contestId, false);
        messaging.convertAndSend(topic(contestId), broadcast);

        if (broadcast.frozen()) {
            // Freeze active: the topic carried the FROZEN board; privileged viewers get the LIVE one.
            StandingsResponse live = standingsService.snapshot(contestId, true);
            for (String username : registry.usernames(contestId)) {
                messaging.convertAndSendToUser(username, queue(contestId), live);
            }
        }
        log.debug("published standings for contest {} (frozen={})", contestId, broadcast.frozen());
    }

    private static String topic(Long contestId) {
        return "/topic/contest/" + contestId + "/standings";
    }

    private static String queue(Long contestId) {
        return "/queue/contest/" + contestId + "/standings";
    }
}
