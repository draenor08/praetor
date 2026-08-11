package com.praetor.identity.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EloCalculatorTest {

    private final EloCalculator calculator =
            new EloCalculator();

    @Test
    void equalRatedWinnerGainsAndLoserLoses() {

        int winnerDelta = calculator.calculateDelta(
                1500,
                1,
                List.of(new EloCalculator.Opponent(1500, 2)));

        int loserDelta = calculator.calculateDelta(
                1500,
                2,
                List.of(new EloCalculator.Opponent(1500, 1)));

        assertThat(winnerDelta).isEqualTo(12);
        assertThat(loserDelta).isEqualTo(-12);
    }

    @Test
    void tiedPlayersWithSameRatingHaveZeroDelta() {

        int delta = calculator.calculateDelta(
                1500,
                1,
                List.of(new EloCalculator.Opponent(1500, 1)));

        assertThat(delta).isZero();
    }

    @Test
    void threeEqualPlayersProduceExpectedDeltas() {

        int first = calculator.calculateDelta(
                1500,
                1,
                List.of(
                        new EloCalculator.Opponent(1500, 2),
                        new EloCalculator.Opponent(1500, 3)));

        int second = calculator.calculateDelta(
                1500,
                2,
                List.of(
                        new EloCalculator.Opponent(1500, 1),
                        new EloCalculator.Opponent(1500, 3)));

        int third = calculator.calculateDelta(
                1500,
                3,
                List.of(
                        new EloCalculator.Opponent(1500, 1),
                        new EloCalculator.Opponent(1500, 2)));

        assertThat(first).isEqualTo(24);
        assertThat(second).isZero();
        assertThat(third).isEqualTo(-24);
    }

    @Test
    void largeContestDeltaIsClamped() {

        List<EloCalculator.Opponent> opponents =
                java.util.stream.IntStream
                        .rangeClosed(2, 10)
                        .mapToObj(rank ->
                                new EloCalculator.Opponent(
                                        1500,
                                        rank))
                        .toList();

        int delta = calculator.calculateDelta(
                1500,
                1,
                opponents);

        assertThat(delta).isEqualTo(48);
    }
}
