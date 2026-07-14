package com.praetor.contest.standings;

import static org.assertj.core.api.Assertions.assertThat;

import com.praetor.contest.dto.ContestProblemDto;
import com.praetor.contest.dto.ProblemCell;
import com.praetor.contest.dto.StandingsResponse;
import com.praetor.contest.dto.StandingsRow;
import com.praetor.contest.repository.StandingsSubmissionRow;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Pure ICPC-fold + freeze logic. No Spring/DB. */
class StandingsCalculatorTest {

    private final StandingsCalculator calc = new StandingsCalculator();

    private static final Instant START = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant END = START.plus(Duration.ofHours(2)); // 120 min window
    private static final long CONTEST = 1L;

    private static final ContestProblemDto A = new ContestProblemDto("A", 1, 100L);
    private static final ContestProblemDto B = new ContestProblemDto("B", 2, 200L);

    /** A submission by user `u` on problem `p` with `verdict` at `min` minutes into the contest. */
    private static StandingsSubmissionRow sub(long u, long p, String verdict, int min) {
        Instant t = START.plus(Duration.ofMinutes(min));
        return new StandingsSubmissionRow() {
            public Long getUserId() { return u; }
            public Long getProblemId() { return p; }
            public String getVerdict() { return verdict; }
            public Instant getCreatedAt() { return t; }
        };
    }

    private static LinkedHashMap<Long, String> participants(Object... idHandlePairs) {
        LinkedHashMap<Long, String> m = new LinkedHashMap<>();
        for (int i = 0; i < idHandlePairs.length; i += 2) {
            m.put((Long) idHandlePairs[i], (String) idHandlePairs[i + 1]);
        }
        return m;
    }

    private StandingsResponse compute(List<StandingsSubmissionRow> subs, int freezeMin,
                                      boolean privileged, int nowMin, LinkedHashMap<Long, String> parts) {
        return calc.compute(CONTEST, START, END, freezeMin, List.of(A, B), parts, subs,
                privileged, START.plus(Duration.ofMinutes(nowMin)));
    }

    private static ProblemCell cell(StandingsRow row, String label) {
        return row.problems().stream().filter(c -> c.label().equals(label)).findFirst().orElseThrow();
    }

    private static StandingsRow rowOf(StandingsResponse board, String handle) {
        return board.rows().stream().filter(r -> r.handle().equals(handle)).findFirst().orElseThrow();
    }

    // ---- ICPC scoring ----

    @Test
    void penaltyIncludesRejectsBeforeAc_andRanksBySolvedThenPenalty() {
        List<StandingsSubmissionRow> subs = List.of(
                sub(10, 100, "WA", 8), sub(10, 100, "AC", 12),   // alice A: 1 reject, solved@12 -> 32
                sub(10, 200, "AC", 33),                          // alice B: solved@33 -> 33
                sub(20, 100, "AC", 5));                          // bob   A: solved@5 -> 5
        StandingsResponse board = compute(subs, 0, false, 60,
                participants(10L, "alice", 20L, "bob"));

        assertThat(board.frozen()).isFalse();
        StandingsRow alice = rowOf(board, "alice");
        assertThat(alice.rank()).isEqualTo(1);
        assertThat(alice.solved()).isEqualTo(2);
        assertThat(alice.penalty()).isEqualTo(32 + 33);
        assertThat(cell(alice, "A").attempts()).isEqualTo(1);
        assertThat(cell(alice, "A").solvedAtMin()).isEqualTo(12);

        StandingsRow bob = rowOf(board, "bob");
        assertThat(bob.rank()).isEqualTo(2);
        assertThat(bob.solved()).isEqualTo(1);
        assertThat(bob.penalty()).isEqualTo(5);
    }

    @Test
    void ceDoesNotCountAsAttempt() {
        List<StandingsSubmissionRow> subs = List.of(
                sub(10, 100, "CE", 2), sub(10, 100, "WA", 4), sub(10, 100, "AC", 10));
        StandingsRow alice = rowOf(
                compute(subs, 0, false, 30, participants(10L, "alice")), "alice");
        assertThat(cell(alice, "A").attempts()).isEqualTo(1); // WA only; CE excluded
        assertThat(alice.penalty()).isEqualTo(10 + 20);
    }

    @Test
    void submissionsAfterAcAreIgnored() {
        List<StandingsSubmissionRow> subs = List.of(
                sub(10, 100, "AC", 5), sub(10, 100, "WA", 8));
        StandingsRow alice = rowOf(
                compute(subs, 0, false, 30, participants(10L, "alice")), "alice");
        assertThat(cell(alice, "A").attempts()).isZero();
        assertThat(alice.penalty()).isEqualTo(5);
    }

    @Test
    void unsolvedProblemShowsAttemptsButNoScore() {
        List<StandingsSubmissionRow> subs = List.of(
                sub(10, 100, "WA", 3), sub(10, 100, "TLE", 9));
        StandingsRow alice = rowOf(
                compute(subs, 0, false, 30, participants(10L, "alice")), "alice");
        assertThat(cell(alice, "A").attempts()).isEqualTo(2);
        assertThat(cell(alice, "A").solvedAtMin()).isNull();
        assertThat(alice.solved()).isZero();
        assertThat(alice.penalty()).isZero();
    }

    @Test
    void tiedRowsShareRank() {
        List<StandingsSubmissionRow> subs = List.of(
                sub(10, 100, "AC", 10),   // alice: solved 1, penalty 10
                sub(20, 200, "AC", 10));  // bob:   solved 1, penalty 10
        StandingsResponse board = compute(subs, 0, false, 30,
                participants(10L, "alice", 20L, "bob", 30L, "carol"));
        assertThat(rowOf(board, "alice").rank()).isEqualTo(1);
        assertThat(rowOf(board, "bob").rank()).isEqualTo(1);
        assertThat(rowOf(board, "carol").rank()).isEqualTo(3); // no ties above are skipped
    }

    @Test
    void outOfWindowSubmissionsIgnored() {
        List<StandingsSubmissionRow> subs = List.of(
                sub(10, 100, "AC", -5),   // before start
                sub(10, 200, "AC", 130)); // after end
        StandingsRow alice = rowOf(
                compute(subs, 0, false, 60, participants(10L, "alice")), "alice");
        assertThat(alice.solved()).isZero();
    }

    // ---- freeze ----

    @Test
    void freezeHidesPostFreezeActivityFromContestant() {
        // freeze last 30 min -> freezeStart @90; now @100 (inside window). AC lands @95 (hidden).
        List<StandingsSubmissionRow> subs = List.of(
                sub(10, 100, "WA", 80), sub(10, 100, "AC", 95));
        StandingsResponse board = compute(subs, 30, false, 100, participants(10L, "alice"));

        assertThat(board.frozen()).isTrue();
        StandingsRow alice = rowOf(board, "alice");
        ProblemCell a = cell(alice, "A");
        assertThat(a.attempts()).isEqualTo(1);      // pre-freeze WA visible
        assertThat(a.solvedAtMin()).isNull();       // AC hidden
        assertThat(a.frozen()).isTrue();            // pending "?"
        assertThat(alice.solved()).isZero();
    }

    @Test
    void privilegedSeesThroughFreeze() {
        List<StandingsSubmissionRow> subs = List.of(
                sub(10, 100, "WA", 80), sub(10, 100, "AC", 95));
        StandingsResponse board = compute(subs, 30, true, 100, participants(10L, "alice"));

        assertThat(board.frozen()).isTrue(); // window is active regardless of viewer
        StandingsRow alice = rowOf(board, "alice");
        ProblemCell a = cell(alice, "A");
        assertThat(a.solvedAtMin()).isEqualTo(95);
        assertThat(a.frozen()).isFalse();
        assertThat(alice.penalty()).isEqualTo(95 + 20);
    }

    @Test
    void solvedBeforeFreezeIsNotAFrozenCell() {
        // AC @50 (pre-freeze), then a hidden WA @95 during freeze.
        List<StandingsSubmissionRow> subs = List.of(
                sub(10, 100, "AC", 50), sub(10, 100, "WA", 95));
        StandingsResponse board = compute(subs, 30, false, 100, participants(10L, "alice"));

        ProblemCell a = cell(rowOf(board, "alice"), "A");
        assertThat(a.solvedAtMin()).isEqualTo(50);
        assertThat(a.frozen()).isFalse();
    }

    @Test
    void freezeInactiveBeforeWindow() {
        // now @50, freeze @90 -> not yet frozen; AC @40 fully scored.
        List<StandingsSubmissionRow> subs = List.of(sub(10, 100, "AC", 40));
        StandingsResponse board = compute(subs, 30, false, 50, participants(10L, "alice"));
        assertThat(board.frozen()).isFalse();
        assertThat(cell(rowOf(board, "alice"), "A").solvedAtMin()).isEqualTo(40);
    }

    @Test
    void participantWithNoSubmissionsHasEmptyCells() {
        StandingsResponse board = compute(List.of(), 0, false, 60, participants(10L, "alice"));
        StandingsRow alice = rowOf(board, "alice");
        assertThat(alice.solved()).isZero();
        assertThat(alice.problems()).hasSize(2);
        assertThat(cell(alice, "A").attempts()).isZero();
        assertThat(cell(alice, "A").solvedAtMin()).isNull();
        assertThat(cell(alice, "A").frozen()).isFalse();
    }
}
