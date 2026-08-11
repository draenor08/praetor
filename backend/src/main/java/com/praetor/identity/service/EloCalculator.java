package com.praetor.identity.service;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class EloCalculator {

    static final int K_FACTOR = 24;
    static final int MAX_DELTA = 48;

    public int calculateDelta(
            int playerRating,
            int playerRank,
            List<Opponent> opponents) {

        if (opponents.isEmpty()) {
            return 0;
        }

        double actualScore = 0.0;
        double expectedScore = 0.0;

        for (Opponent opponent : opponents) {

            if (playerRank < opponent.rank()) {
                actualScore += 1.0;
            } else if (playerRank == opponent.rank()) {
                actualScore += 0.5;
            }

            expectedScore += expectedScore(
                    playerRating,
                    opponent.rating());
        }

        int delta = (int) Math.round(
                K_FACTOR * (actualScore - expectedScore));

        return Math.max(
                -MAX_DELTA,
                Math.min(MAX_DELTA, delta));
    }

    private double expectedScore(
            int playerRating,
            int opponentRating) {

        return 1.0 / (
                1.0 + Math.pow(
                        10.0,
                        (opponentRating - playerRating) / 400.0));
    }

    public record Opponent(
            int rating,
            int rank) {
    }
}
