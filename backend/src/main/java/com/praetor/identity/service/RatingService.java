package com.praetor.identity.service;

import com.praetor.contest.dto.StandingsResponse;
import com.praetor.contest.dto.StandingsRow;
import com.praetor.contest.standings.StandingsService;
import com.praetor.identity.dto.LeaderboardEntry;
import com.praetor.identity.dto.LeaderboardResponse;
import com.praetor.identity.dto.RatingHistoryResponse;
import com.praetor.identity.dto.RatingResponse;
import com.praetor.identity.entity.Rating;
import com.praetor.identity.entity.RatingHistory;
import com.praetor.identity.entity.User;
import com.praetor.identity.repository.RatingHistoryRepository;
import com.praetor.identity.repository.RatingRepository;
import com.praetor.identity.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class RatingService {

    public static final int DEFAULT_RATING = 1500;

    private final RatingRepository ratingRepository;
    private final RatingHistoryRepository ratingHistoryRepository;
    private final UserRepository userRepository;
    private final StandingsService standingsService;
    private final EloCalculator eloCalculator;

    public RatingService(
            RatingRepository ratingRepository,
            RatingHistoryRepository ratingHistoryRepository,
            UserRepository userRepository,
            StandingsService standingsService,
            EloCalculator eloCalculator) {

        this.ratingRepository = ratingRepository;
        this.ratingHistoryRepository = ratingHistoryRepository;
        this.userRepository = userRepository;
        this.standingsService = standingsService;
        this.eloCalculator = eloCalculator;
    }

    @Transactional(readOnly = true)
    public RatingResponse getUserRating(String handle) {

        User user = userRepository.findByUsername(handle)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "user not found"));

        int rating = ratingRepository.findById(user.getId())
                .map(Rating::getValue)
                .orElse(DEFAULT_RATING);

        long rank =
                ratingRepository.countByValueGreaterThan(rating) + 1;

        List<RatingHistoryResponse> history =
                ratingHistoryRepository
                        .findByUserIdOrderByCreatedAtAsc(user.getId())
                        .stream()
                        .map(this::toHistoryResponse)
                        .toList();

        return new RatingResponse(
                rating,
                rank,
                history);
    }

    @Transactional(readOnly = true)
    public LeaderboardResponse getLeaderboard(
            int page,
            int size) {

        if (page < 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "page must be >= 0");
        }

        if (size < 1 || size > 100) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "size must be between 1 and 100");
        }

        List<LeaderboardEntry> content =
                ratingRepository
                        .findLeaderboardPage(size, page * size)
                        .stream()
                        .map(row -> new LeaderboardEntry(
                                row.getRank(),
                                row.getHandle(),
                                row.getRating()))
                        .toList();

        return new LeaderboardResponse(
                content,
                page,
                size,
                ratingRepository.count());
    }

    /**
     * ADMIN-triggered rating application, for when waiting out the scheduler's tick is not
     * acceptable (a live demo, say). Idempotent by the same guard as the scheduled path: a
     * contest that already has rating history is a no-op, so pressing it twice cannot
     * double-apply.
     */
    @Transactional
    public void applyContestResults(Long contestId, User user) {

        if (user == null || !"ADMIN".equals(user.getRole())) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "only ADMIN may apply contest ratings");
        }

        applyContestResults(contestId);
    }

    @Transactional
    public void applyContestResults(Long contestId) {

        if (ratingHistoryRepository.existsByContestId(contestId)) {
            return;
        }

        StandingsResponse standings =
                standingsService.snapshot(contestId, true);

        List<StandingsRow> rows = standings.rows();

        if (rows.isEmpty()) {
            return;
        }

        // Two statements for the whole field, not two per participant. The board hands us every
        // handle up front, so there is nothing to discover row by row.
        List<String> handles = rows.stream()
                .map(StandingsRow::handle)
                .toList();

        Map<String, User> usersByHandle = userRepository
                .findByUsernameIn(handles)
                .stream()
                .collect(Collectors.toMap(
                        User::getUsername,
                        u -> u));

        if (usersByHandle.size() != handles.size()) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "standings user not found");
        }

        List<Long> userIds = handles.stream()
                .map(handle -> usersByHandle.get(handle).getId())
                .toList();

        Map<Long, Rating> ratingsByUserId = ratingRepository
                .findAllById(userIds)
                .stream()
                .collect(Collectors.toMap(
                        Rating::getUserId,
                        r -> r));

        List<ParticipantRating> participants =
                new ArrayList<>();

        for (StandingsRow row : rows) {

            User user = usersByHandle.get(row.handle());

            // A user who has never been rated has no row yet; it is created here and saved below.
            Rating rating = ratingsByUserId.computeIfAbsent(
                    user.getId(),
                    id -> new Rating(id, DEFAULT_RATING));

            participants.add(
                    new ParticipantRating(
                            user,
                            rating,
                            rating.getValue(),
                            row.rank()));
        }

        List<RatingChange> changes =
                new ArrayList<>();

        for (ParticipantRating participant : participants) {

            List<EloCalculator.Opponent> opponents =
                    participants.stream()
                            .filter(other ->
                                    !other.user().getId()
                                            .equals(participant.user().getId()))
                            .map(other ->
                                    new EloCalculator.Opponent(
                                            other.before(),
                                            other.rank()))
                            .toList();

            int delta = eloCalculator.calculateDelta(
                    participant.before(),
                    participant.rank(),
                    opponents);

            int after =
                    participant.before() + delta;

            changes.add(
                    new RatingChange(
                            participant,
                            after));
        }

        List<Rating> toSave = new ArrayList<>();
        List<RatingHistory> history = new ArrayList<>();

        for (RatingChange change : changes) {

            ParticipantRating participant =
                    change.participant();

            participant.rating()
                    .setValue(change.after());

            toSave.add(participant.rating());

            history.add(
                    new RatingHistory(
                            participant.user().getId(),
                            contestId,
                            participant.before(),
                            change.after()));
        }

        // One flush each, rather than two writes per participant.
        ratingRepository.saveAll(toSave);
        ratingHistoryRepository.saveAll(history);
    }

    private RatingHistoryResponse toHistoryResponse(
            RatingHistory history) {

        return new RatingHistoryResponse(
                history.getContestId(),
                history.getRatingBefore(),
                history.getRatingAfter(),
                history.getCreatedAt()
                        .toInstant()
                        .toString());
    }

    private record ParticipantRating(
            User user,
            Rating rating,
            int before,
            int rank) {
    }

    private record RatingChange(
            ParticipantRating participant,
            int after) {
    }
}
