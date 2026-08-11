package com.praetor.submission.service;

import java.time.Clock;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Per-user cooldown between accepted submissions (FR-17).
 *
 * <p>Enforced here rather than in a servlet filter because a filter runs <em>before</em> the
 * handler and therefore cannot tell an accepted submission from a rejected one — the previous
 * implementation stamped the clock on the way in, so a 404 for a mistyped slug locked the user
 * out for the whole window. Only a submission that passes validation and reaches the judge
 * spends the window.
 *
 * <p>State is one timestamp per user, mutated through {@link ConcurrentHashMap#compute} so the
 * check-and-record pair is atomic per user without serialising every submitter behind one lock.
 * A sweep drops entries older than the window, so the map tracks recent submitters rather than
 * growing once per user forever.
 */
@Component
public class SubmissionRateLimiter {

    private final Map<Long, Long> lastAcceptedByUser = new ConcurrentHashMap<>();
    private final long windowMillis;
    private final Clock clock;

    // @Autowired is required, not decorative: the test-seam constructor below means this class has
    // two, and Spring only infers the injection point when there is exactly one.
    @Autowired
    public SubmissionRateLimiter(
            @Value("${praetor.rate-limit.submission-seconds:10}") long windowSeconds) {
        this(windowSeconds, Clock.systemUTC());
    }

    /** Test seam: lets a suite drive the window without sleeping through it. */
    SubmissionRateLimiter(long windowSeconds, Clock clock) {
        this.windowMillis = windowSeconds * 1000L;
        this.clock = clock;
    }

    /**
     * Records this user's submission, or refuses it if they are still inside the cooldown.
     *
     * @throws SubmissionRateLimitedException with the whole seconds left to wait
     */
    public void recordOrReject(Long userId) {
        if (windowMillis <= 0) {
            return;
        }

        long now = clock.millis();
        long[] waitMillis = {0};

        lastAcceptedByUser.compute(userId, (id, lastAccepted) -> {
            if (lastAccepted != null && now - lastAccepted < windowMillis) {
                waitMillis[0] = windowMillis - (now - lastAccepted);
                return lastAccepted;
            }
            return now;
        });

        if (waitMillis[0] > 0) {
            // round up: "wait 0 seconds" would invite an immediate retry that fails again
            long retryAfterSec = (waitMillis[0] + 999) / 1000;
            throw new SubmissionRateLimitedException(retryAfterSec);
        }
    }

    /** Drops users whose cooldown has long expired; without it the map only ever grows. */
    @Scheduled(fixedDelayString = "${praetor.rate-limit.sweep-ms:300000}")
    public void evictExpired() {
        long cutoff = clock.millis() - windowMillis;
        lastAcceptedByUser.entrySet().removeIf(entry -> entry.getValue() < cutoff);
    }

    /** Visible for tests. */
    int trackedUsers() {
        return lastAcceptedByUser.size();
    }
}
