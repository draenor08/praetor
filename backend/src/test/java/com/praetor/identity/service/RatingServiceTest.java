package com.praetor.identity.service;

import com.praetor.contest.dto.StandingsResponse;
import com.praetor.contest.dto.StandingsRow;
import com.praetor.contest.standings.StandingsService;
import com.praetor.identity.entity.Rating;
import com.praetor.identity.entity.RatingHistory;
import com.praetor.identity.entity.User;
import com.praetor.identity.repository.RatingHistoryRepository;
import com.praetor.identity.repository.RatingRepository;
import com.praetor.identity.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RatingServiceTest {

    private final RatingRepository ratingRepository =
            mock(RatingRepository.class);

    private final RatingHistoryRepository ratingHistoryRepository =
            mock(RatingHistoryRepository.class);

    private final UserRepository userRepository =
            mock(UserRepository.class);

    private final StandingsService standingsService =
            mock(StandingsService.class);

    private final EloCalculator eloCalculator =
            new EloCalculator();

    private final RatingService service =
            new RatingService(
                    ratingRepository,
                    ratingHistoryRepository,
                    userRepository,
                    standingsService,
                    eloCalculator);

    @Test
    void getUserRatingReturnsRatingRankAndHistory() {

        User alice = user(1L, "alice");

        RatingHistory history = mock(RatingHistory.class);

        when(history.getContestId()).thenReturn(10L);
        when(history.getRatingBefore()).thenReturn(1500);
        when(history.getRatingAfter()).thenReturn(1524);
        when(history.getCreatedAt()).thenReturn(
                ZonedDateTime.parse("2026-08-01T10:00:00Z"));

        when(userRepository.findByUsername("alice"))
                .thenReturn(Optional.of(alice));

        when(ratingRepository.findById(1L))
                .thenReturn(Optional.of(new Rating(1L, 1524)));

        when(ratingRepository.countByValueGreaterThan(1524))
                .thenReturn(2L);

        when(ratingHistoryRepository
                .findByUserIdOrderByCreatedAtAsc(1L))
                .thenReturn(List.of(history));

        var response = service.getUserRating("alice");

        assertThat(response.rating()).isEqualTo(1524);
        assertThat(response.rank()).isEqualTo(3);
        assertThat(response.history()).hasSize(1);

        assertThat(response.history().get(0).contestId())
                .isEqualTo(10L);

        assertThat(response.history().get(0).before())
                .isEqualTo(1500);

        assertThat(response.history().get(0).after())
                .isEqualTo(1524);
    }

    @Test
    void getUserRatingUses1500WhenRatingRowMissing() {

        User bob = user(2L, "bob");

        when(userRepository.findByUsername("bob"))
                .thenReturn(Optional.of(bob));

        when(ratingRepository.findById(2L))
                .thenReturn(Optional.empty());

        when(ratingRepository.countByValueGreaterThan(1500))
                .thenReturn(1L);

        when(ratingHistoryRepository
                .findByUserIdOrderByCreatedAtAsc(2L))
                .thenReturn(List.of());

        var response = service.getUserRating("bob");

        assertThat(response.rating()).isEqualTo(1500);
        assertThat(response.rank()).isEqualTo(2);
        assertThat(response.history()).isEmpty();
    }

    @Test
    void leaderboardReturnsUsersInRatingOrder() {

        // built before the stub below: row() stubs its own mock, and Mockito refuses a
        // stubbing that starts while another when(...) is still unfinished
        var alice = row(1L, "alice", 1800);
        var bob = row(2L, "bob", 1700);

        when(ratingRepository.findLeaderboardPage(20, 0))
                .thenReturn(List.of(alice, bob));

        when(ratingRepository.count()).thenReturn(2L);

        var response =
                service.getLeaderboard(0, 20);

        assertThat(response.content()).hasSize(2);
        assertThat(response.totalElements()).isEqualTo(2L);

        assertThat(response.content().get(0).handle())
                .isEqualTo("alice");

        assertThat(response.content().get(0).rating())
                .isEqualTo(1800);

        assertThat(response.content().get(0).rank())
                .isEqualTo(1);

        assertThat(response.content().get(1).handle())
                .isEqualTo("bob");

        assertThat(response.content().get(1).rank())
                .isEqualTo(2);
    }

    @Test
    void leaderboardReadsOnePageInOneQuery() {

        var alice = row(1L, "alice", 1800);

        when(ratingRepository.findLeaderboardPage(20, 40))
                .thenReturn(List.of(alice));

        service.getLeaderboard(2, 20);

        // page 2 → offset 40, and no per-row user/rank lookups (the old shape was 2N+1)
        verify(ratingRepository).findLeaderboardPage(20, 40);
        verify(userRepository, never()).findById(any());
        verify(ratingRepository, never()).countByValueGreaterThan(any());
    }

    @Test
    void leaderboardRejectsOutOfRangePaging() {

        assertThat(catchThrowable(() -> service.getLeaderboard(-1, 20)))
                .isInstanceOf(ResponseStatusException.class);

        assertThat(catchThrowable(() -> service.getLeaderboard(0, 101)))
                .isInstanceOf(ResponseStatusException.class);

        verify(ratingRepository, never())
                .findLeaderboardPage(anyInt(), anyInt());
    }

    @Test
    void onlyAdminMayApplyContestRatingsManually() {

        User contestant = user(1L, "alice");
        contestant.setRole("USER");

        Throwable t = catchThrowable(() ->
                service.applyContestResults(10L, contestant));

        assertThat(t).isInstanceOf(ResponseStatusException.class);
        assertThat(((ResponseStatusException) t).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);

        verify(standingsService, never())
                .snapshot(any(), anyBoolean());
    }

    private RatingRepository.LeaderboardRow row(long rank, String handle, int rating) {
        RatingRepository.LeaderboardRow row =
                mock(RatingRepository.LeaderboardRow.class);
        when(row.getRank()).thenReturn(rank);
        when(row.getHandle()).thenReturn(handle);
        when(row.getRating()).thenReturn(rating);
        return row;
    }

    @Test
    void applyContestResultsUpdatesRatingsAndWritesHistory() {

        long contestId = 10L;

        User alice = user(1L, "alice");
        User bob = user(2L, "bob");

        Rating aliceRating = new Rating(1L, 1500);
        Rating bobRating = new Rating(2L, 1500);

        StandingsResponse standings =
                new StandingsResponse(
                        contestId,
                        false,
                        "2026-08-11T00:00:00Z",
                        List.of(
                                new StandingsRow(
                                        1,
                                        "alice",
                                        2,
                                        40,
                                        List.of()),
                                new StandingsRow(
                                        2,
                                        "bob",
                                        1,
                                        60,
                                        List.of())));

        when(ratingHistoryRepository.existsByContestId(contestId))
                .thenReturn(false);

        when(standingsService.snapshot(contestId, true))
                .thenReturn(standings);

        when(userRepository.findByUsername("alice"))
                .thenReturn(Optional.of(alice));

        when(userRepository.findByUsername("bob"))
                .thenReturn(Optional.of(bob));

        when(ratingRepository.findById(1L))
                .thenReturn(Optional.of(aliceRating));

        when(ratingRepository.findById(2L))
                .thenReturn(Optional.of(bobRating));

        service.applyContestResults(contestId);

        assertThat(aliceRating.getValue())
                .isEqualTo(1512);

        assertThat(bobRating.getValue())
                .isEqualTo(1488);

        verify(ratingRepository, times(2))
                .save(any(Rating.class));

        ArgumentCaptor<RatingHistory> historyCaptor =
                ArgumentCaptor.forClass(RatingHistory.class);

        verify(ratingHistoryRepository, times(2))
                .save(historyCaptor.capture());

        List<RatingHistory> histories =
                historyCaptor.getAllValues();

        assertThat(histories)
                .extracting(RatingHistory::getContestId)
                .containsOnly(contestId);

        assertThat(histories)
                .extracting(RatingHistory::getRatingBefore)
                .containsExactlyInAnyOrder(1500, 1500);

        assertThat(histories)
                .extracting(RatingHistory::getRatingAfter)
                .containsExactlyInAnyOrder(1512, 1488);
    }

    @Test
    void applyContestResultsDoesNothingWhenContestAlreadyApplied() {

        long contestId = 10L;

        when(ratingHistoryRepository.existsByContestId(contestId))
                .thenReturn(true);

        service.applyContestResults(contestId);

        verify(standingsService, never())
                .snapshot(any(), any(Boolean.class));

        verify(ratingRepository, never())
                .save(any(Rating.class));

        verify(ratingHistoryRepository, never())
                .save(any(RatingHistory.class));
    }

    private User user(Long id, String username) {

        User user = new User();
        user.setId(id);
        user.setUsername(username);

        return user;
    }
}
