package com.praetor.submission.engine;

/**
 * Per-run limits handed to the sandbox and the evaluator.
 *
 * @param softWallMs  per-problem time limit; exceeding it (but finishing) = TLE
 * @param hardWallMs  absolute wall-clock kill ceiling (cpuSeconds*1000); the process is killed here
 * @param memMb       container memory cap for {@code docker run --memory}
 * @param pidsMax     container {@code --pids-limit}
 * @param memLimitKb  per-problem memory limit; peak RSS ≥ this = MLE
 */
public record RunLimits(int softWallMs, int hardWallMs, int memMb, int pidsMax, int memLimitKb) {
}
