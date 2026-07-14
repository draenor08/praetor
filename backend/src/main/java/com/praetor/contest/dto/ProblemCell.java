package com.praetor.contest.dto;

/**
 * One participant's result on one contest problem.
 *
 * @param attempts    rejected submissions BEFORE the AC (the AC attempt is not counted; CE never
 *                    counts). For an unsolved problem, all rejected non-CE attempts so far.
 * @param solvedAtMin whole minutes from contest start to the accepted submission; {@code null} if
 *                    unsolved.
 * @param frozen      true when post-freeze activity on this cell is hidden from this viewer (render
 *                    as a pending "?"). Only ever true on a non-privileged viewer's board during a
 *                    freeze window, for a cell not already solved before the freeze.
 */
public record ProblemCell(
        String label,
        int attempts,
        Integer solvedAtMin,
        boolean frozen) {
}
