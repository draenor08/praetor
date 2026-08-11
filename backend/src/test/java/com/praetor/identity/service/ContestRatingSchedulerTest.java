package com.praetor.identity.service;

import com.praetor.identity.repository.RatedContestRepository;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ContestRatingSchedulerTest {

    private final RatedContestRepository ratedContestRepo =
            mock(RatedContestRepository.class);

    private final RatingService ratingService =
            mock(RatingService.class);

    private final ContestRatingScheduler scheduler =
            new ContestRatingScheduler(ratedContestRepo, ratingService);

    @Test
    void ratesEveryContestTheQueryReturns() {

        when(ratedContestRepo.findContestIdsAwaitingRating())
                .thenReturn(List.of(10L, 11L));

        scheduler.applyPendingContestRatings();

        verify(ratingService).applyContestResults(10L);
        verify(ratingService).applyContestResults(11L);
    }

    @Test
    void doesNothingWhenNoContestIsWaiting() {

        when(ratedContestRepo.findContestIdsAwaitingRating())
                .thenReturn(List.of());

        scheduler.applyPendingContestRatings();

        verifyNoInteractions(ratingService);
    }

    @Test
    void oneFailingContestDoesNotStopTheRest() {

        when(ratedContestRepo.findContestIdsAwaitingRating())
                .thenReturn(List.of(10L, 11L, 12L));

        doThrow(new IllegalStateException("standings user vanished"))
                .when(ratingService).applyContestResults(11L);

        scheduler.applyPendingContestRatings();

        // the failure is swallowed and 12 still gets rated in the same tick
        verify(ratingService).applyContestResults(10L);
        verify(ratingService).applyContestResults(12L);
    }

    @Test
    void neverRatesAContestTheQueryExcluded() {

        // the query already filters out running, unregistered and already-rated contests;
        // the scheduler must not second-guess it or re-scan everything itself
        when(ratedContestRepo.findContestIdsAwaitingRating())
                .thenReturn(List.of(10L));

        scheduler.applyPendingContestRatings();

        verify(ratingService, never()).applyContestResults(99L);
    }
}
