package com.praetor.contest.standings;

import com.praetor.contest.dto.ContestProblemDto;
import com.praetor.contest.dto.ProblemCell;
import com.praetor.contest.dto.StandingsResponse;
import com.praetor.contest.dto.StandingsRow;
import com.praetor.contest.repository.StandingsSubmissionRow;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Pure ICPC standings fold — no Spring/DB dependencies so it is exhaustively unit-testable. Given a
 * contest window, its problems, its participants, and every judged submission, produces the FROZEN
 * board contract.
 *
 * <p><b>ICPC scoring (frozen):</b> rank by {@code solved} desc then {@code penalty} asc;
 * {@code penalty = Σ over solved problems (solvedAtMin + 20 × rejectedAttemptsBeforeAC)}; CE never
 * counts as a rejected attempt; unsolved problems contribute nothing.
 *
 * <p><b>Freeze (frozen):</b> a freeze window is {@code [endsAt − freezeMin, endsAt)}. When it is
 * active AND the viewer is not privileged, submissions at/after the freeze start are hidden: they do
 * not affect the score, and an unsolved cell with such hidden activity is flagged {@code frozen}
 * (render "?"). A privileged viewer ({@code privileged=true}) sees through the freeze — full live
 * board, no frozen cells — even though the board-level {@code frozen} flag still reports the window
 * is active.
 */
@Component
public class StandingsCalculator {

    private static final int PENALTY_PER_REJECT = 20;
    private static final String AC = "AC";
    private static final String CE = "CE";

    /**
     * @param participants userId → handle, in the order rows should be built (final order is by
     *                     rank after sorting). Only these users appear on the board.
     * @param privileged   true for ADMIN/PROBLEM_SETTER viewers who see through an active freeze.
     */
    public StandingsResponse compute(Long contestId, Instant startsAt, Instant endsAt, int freezeMin,
                                     List<ContestProblemDto> problems,
                                     LinkedHashMap<Long, String> participants,
                                     List<StandingsSubmissionRow> subs, boolean privileged,
                                     Instant now) {
        Instant freezeStart = freezeMin > 0 ? endsAt.minus(Duration.ofMinutes(freezeMin)) : endsAt;
        boolean freezeActive = freezeMin > 0 && !now.isBefore(freezeStart) && now.isBefore(endsAt);
        boolean hide = freezeActive && !privileged;

        // Index submissions by user then problem, preserving the oldest-first ordering of the query.
        Map<Long, Map<Long, List<StandingsSubmissionRow>>> byUserProblem = new java.util.HashMap<>();
        for (StandingsSubmissionRow s : subs) {
            byUserProblem
                    .computeIfAbsent(s.getUserId(), k -> new java.util.HashMap<>())
                    .computeIfAbsent(s.getProblemId(), k -> new ArrayList<>())
                    .add(s);
        }

        List<StandingsRow> rows = new ArrayList<>();
        for (Map.Entry<Long, String> participant : participants.entrySet()) {
            Long userId = participant.getKey();
            Map<Long, List<StandingsSubmissionRow>> perProblem =
                    byUserProblem.getOrDefault(userId, Map.of());

            List<ProblemCell> cells = new ArrayList<>();
            int solved = 0;
            int penalty = 0;
            for (ContestProblemDto p : problems) {
                ProblemCell cell = cellFor(p, perProblem.getOrDefault(p.problemId(), List.of()),
                        startsAt, endsAt, freezeStart, hide);
                cells.add(cell);
                if (cell.solvedAtMin() != null) {
                    solved++;
                    penalty += cell.solvedAtMin() + PENALTY_PER_REJECT * cell.attempts();
                }
            }
            rows.add(new StandingsRow(0, participant.getValue(), solved, penalty, cells));
        }

        rank(rows);
        return new StandingsResponse(contestId, freezeActive, now.toString(), rows);
    }

    /** Fold one participant's submissions for one problem into a cell. */
    private ProblemCell cellFor(ContestProblemDto p, List<StandingsSubmissionRow> subs,
                                Instant startsAt, Instant endsAt, Instant freezeStart, boolean hide) {
        int attempts = 0;
        Integer solvedAtMin = null;
        boolean hasHiddenActivity = false;

        for (StandingsSubmissionRow s : subs) {
            Instant t = s.getCreatedAt();
            if (t.isBefore(startsAt) || t.isAfter(endsAt)) {
                continue; // out of the contest window
            }
            boolean counted = !hide || t.isBefore(freezeStart);
            if (!counted) {
                hasHiddenActivity = true;
                continue;
            }
            if (solvedAtMin != null) {
                continue; // already accepted — later submissions don't change the cell
            }
            if (AC.equals(s.getVerdict())) {
                solvedAtMin = (int) Duration.between(startsAt, t).toMinutes();
            } else if (!CE.equals(s.getVerdict())) {
                attempts++;
            }
        }

        boolean frozen = hide && solvedAtMin == null && hasHiddenActivity;
        return new ProblemCell(p.label(), attempts, solvedAtMin, frozen);
    }

    /** Sort by solved desc, penalty asc; ties (equal solved AND penalty) share a rank number. */
    private void rank(List<StandingsRow> rows) {
        rows.sort(Comparator
                .comparingInt(StandingsRow::solved).reversed()
                .thenComparingInt(StandingsRow::penalty));
        List<StandingsRow> ranked = new ArrayList<>(rows.size());
        int rank = 0;
        StandingsRow prev = null;
        for (int i = 0; i < rows.size(); i++) {
            StandingsRow r = rows.get(i);
            if (prev == null || r.solved() != prev.solved() || r.penalty() != prev.penalty()) {
                rank = i + 1;
            }
            ranked.add(new StandingsRow(rank, r.handle(), r.solved(), r.penalty(), r.problems()));
            prev = r;
        }
        rows.clear();
        rows.addAll(ranked);
    }
}
