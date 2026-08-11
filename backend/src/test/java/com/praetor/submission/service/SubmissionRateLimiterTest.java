package com.praetor.submission.service;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.catchThrowable;

class SubmissionRateLimiterTest {

    /** Hand-cranked clock: the window is driven forward without sleeping through it. */
    private static final class TestClock extends Clock {
        private Instant now = Instant.parse("2026-08-11T12:00:00Z");

        void advanceSeconds(long seconds) {
            now = now.plusSeconds(seconds);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }
    }

    private final TestClock clock = new TestClock();
    private final SubmissionRateLimiter limiter = new SubmissionRateLimiter(10, clock);

    @Test
    void firstSubmissionIsAccepted() {
        assertThatCode(() -> limiter.recordOrReject(1L))
                .doesNotThrowAnyException();
    }

    @Test
    void secondSubmissionInsideTheWindowIsRejectedWithTimeRemaining() {

        limiter.recordOrReject(1L);
        clock.advanceSeconds(3);

        Throwable t = catchThrowable(() -> limiter.recordOrReject(1L));

        assertThat(t).isInstanceOf(SubmissionRateLimitedException.class);
        assertThat(((SubmissionRateLimitedException) t).getRetryAfterSeconds())
                .isEqualTo(7);
    }

    @Test
    void submissionIsAcceptedOnceTheWindowExpires() {

        limiter.recordOrReject(1L);
        clock.advanceSeconds(10);

        assertThatCode(() -> limiter.recordOrReject(1L))
                .doesNotThrowAnyException();
    }

    @Test
    void aRejectedAttemptDoesNotExtendTheWindow() {

        limiter.recordOrReject(1L);
        clock.advanceSeconds(5);

        // bounces, and must NOT re-stamp the clock — otherwise hammering Submit would
        // hold the user out forever
        catchThrowable(() -> limiter.recordOrReject(1L));

        clock.advanceSeconds(5);

        assertThatCode(() -> limiter.recordOrReject(1L))
                .doesNotThrowAnyException();
    }

    @Test
    void cooldownsAreTrackedPerUser() {

        limiter.recordOrReject(1L);

        assertThatCode(() -> limiter.recordOrReject(2L))
                .doesNotThrowAnyException();
    }

    @Test
    void sweepDropsExpiredUsersOnly() {

        limiter.recordOrReject(1L);
        clock.advanceSeconds(11);
        limiter.recordOrReject(2L);

        assertThat(limiter.trackedUsers()).isEqualTo(2);

        limiter.evictExpired();

        // user 1's cooldown lapsed 11s ago; user 2 is still inside theirs
        assertThat(limiter.trackedUsers()).isEqualTo(1);
        assertThat(catchThrowable(() -> limiter.recordOrReject(2L)))
                .isInstanceOf(SubmissionRateLimitedException.class);
    }

    @Test
    void zeroSecondsDisablesTheLimitEntirely() {

        SubmissionRateLimiter disabled =
                new SubmissionRateLimiter(0, clock);

        disabled.recordOrReject(1L);

        assertThatCode(() -> disabled.recordOrReject(1L))
                .doesNotThrowAnyException();
    }
}
